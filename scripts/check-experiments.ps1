# =====================================================================
# check-experiments.ps1  -  a PowerShell script (.ps1 = a text file of PowerShell commands).
# It tests the experiment URLs of experiment-service: create, read, update, history, delete,
# the safety limits and the error answers. The password is asked with stars, never printed or saved.
# It creates ONE test experiment and deletes it at the end.
#
# BEFORE you run it, start BOTH services (two windows):
#     auth-service        (port 8081)   .\mvnw.cmd spring-boot:run
#     experiment-service  (port 8082)   .\mvnw.cmd spring-boot:run
# Then, from the project root (the folder that contains "infra"):
#     .\scripts\check-experiments.ps1
# =====================================================================

param(
    [string]$AuthUrl       = "http://localhost:8081",
    [string]$ExperimentUrl = "http://localhost:8082",
    [string]$Username      = "developer"
)

$secure = Read-Host "Password for '$Username'" -AsSecureString
$plain  = [System.Net.NetworkCredential]::new("", $secure).Password

function Show-Result {
    param([string]$Name, [bool]$Ok, $Detail)
    if ($Ok) { Write-Host ("PASS  " + $Name + "  " + $Detail) -ForegroundColor Green }
    else     { Write-Host ("FAIL  " + $Name + "  " + $Detail) -ForegroundColor Red }
}

# Calls a URL and returns the status number AND the text of the answer (also for 4xx errors).
# We read the answer as TEXT (not as an object) because the answer has two spellings of some
# names (flexibleResources and flexibleresources), and Windows PowerShell 5.1 cannot read that as an object.
function Call-Api {
    param([string]$Method, [string]$Url, [string]$Body = $null)
    $args2 = @{ Uri = $Url; Method = $Method; WebSession = $script:session; UseBasicParsing = $true }
    if ($Body) { $args2.ContentType = "application/json"; $args2.Body = $Body }
    try {
        $r = Invoke-WebRequest @args2
        return @{ Status = [int]$r.StatusCode; Text = [string]$r.Content }
    } catch {
        $status = -1; $text = ""
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $text = $reader.ReadToEnd()
            } catch { }
        }
        return @{ Status = $status; Text = $text }
    }
}

# 1. Log in to auth-service to get the cookie, then give the same cookie to experiment-service.
$loginBody = @{ username = $Username; password = $plain } | ConvertTo-Json
try {
    Invoke-WebRequest -Uri "$AuthUrl/api/auth/login" -Method Post -ContentType "application/json" -Body $loginBody -SessionVariable session -UseBasicParsing | Out-Null
    Show-Result "login" $true ""
} catch {
    Show-Result "login" $false $_.Exception.Message
    return
}
foreach ($c in $session.Cookies.GetCookies([uri]$AuthUrl)) {
    $session.Cookies.Add([uri]$ExperimentUrl, (New-Object System.Net.Cookie($c.Name, $c.Value, "/", "localhost")))
}

# 2. Create an experiment. The values are on purpose WRONG, to test the limits:
#    flexibleResources=0 but phoneBoomChute=1  -> phoneBoomChute must become 0
#    minimumCageOccupancy=10                    -> must become 50
#    maximumCageOccupancy=95                    -> must become 90
#    sorterFullPercent=150                      -> must become 100
#    startTime is only "23:00:00"               -> must become 2026-10-19T23:00:00 (date from selectedDate)
$createBody = @{
    experimentName = "check-run"; terminal = "TC1"; sortingType = "A"; useCaseId = 1
    selectedDate = "2026-10-19"; startTime = "23:00:00"; endTime = "06:30:00"; parcelCount = 1000
    flexibleResources = 0; phoneBoomChute = 1; minimumCageOccupancy = 10; maximumCageOccupancy = 95
    sorterFullPercent = 150
} | ConvertTo-Json
$r = Call-Api "POST" "$ExperimentUrl/api/experiments/createExperiment" $createBody
Show-Result "create (200)" ($r.Status -eq 200) ("got " + $r.Status)
$id = [regex]::Match($r.Text, '"experimentId":(\d+)').Groups[1].Value
Show-Result "id is 1001 or more" ($id -ne "" -and [int64]$id -ge 1001) ("id=" + $id)
Show-Result "status is PENDING"            ($r.Text -match '"status":"PENDING"') ""
Show-Result "phoneBoomChute forced to 0"   ($r.Text -match '"phoneBoomChute":0') ""
Show-Result "minimum cage forced to 50"    ($r.Text -match '"minimumCageOccupancy":50') ""
Show-Result "maximum cage forced to 90"    ($r.Text -match '"maximumCageOccupancy":90') ""
Show-Result "sorter full forced to 100"    ($r.Text -match '"sorterFullPercent":100') ""
Show-Result "guiMode defaults to GUI"      ($r.Text -match '"guiMode":"GUI"') ""
Show-Result "start time has the full form" ($r.Text -match '"startTime":"2026-10-19T23:00:00"') ""
Show-Result "owner name is filled"         ($r.Text -match ('"username":"' + $Username + '"')) ""
if ($id -eq "") { return }

# 3. Read it back.
$r = Call-Api "GET" "$ExperimentUrl/api/experiments/$id"
Show-Result "get by id (200)" ($r.Status -eq 200 -and $r.Text -match '"experimentName":"check-run"') ("got " + $r.Status)

# 4. Update it (note: only the owner may do this; we are the owner).
$updateBody = @{ experimentName = "check-run-2"; terminal = "TC1"; sortingType = "A"; useCaseId = 1; selectedDate = "2026-10-19"; startTime = "2026-10-19T22:00:00"; endTime = "06:30:00" } | ConvertTo-Json
$r = Call-Api "PUT" "$ExperimentUrl/api/experiments/$id" $updateBody
Show-Result "update (200)" ($r.Status -eq 200 -and $r.Text -match '"experimentName":"check-run-2"' -and $r.Text -match '"startTime":"2026-10-19T22:00:00"') ("got " + $r.Status)

# 5. The special all-sorted-at URL (both spellings are accepted).
$r = Call-Api "PUT" "$ExperimentUrl/api/experiments/$id/all-sorted-at" (@{ allsortedat = "06:15:00" } | ConvertTo-Json)
Show-Result "all-sorted-at (200)" ($r.Status -eq 200 -and $r.Text -match '"allsortedat":"06:15:00"') ("got " + $r.Status)

# 6. History (page 0, 8 rows, newest first) must contain our experiment.
$r = Call-Api "GET" "$ExperimentUrl/api/experiments/history?page=0&size=8&sort=createdAt,desc"
Show-Result "history (200)" ($r.Status -eq 200 -and $r.Text -match '"totalElements":\d+' -and $r.Text -match ('"experimentId":' + $id)) ("got " + $r.Status)
$r = Call-Api "GET" "$ExperimentUrl/api/experiments/history?status=PENDING"
Show-Result "history filter PENDING (200)" ($r.Status -eq 200 -and $r.Text -match ('"experimentId":' + $id)) ("got " + $r.Status)
$r = Call-Api "GET" "$ExperimentUrl/api/experiments/history?status=COMPLETED"
Show-Result "history filter COMPLETED does not list it" ($r.Status -eq 200 -and $r.Text -notmatch ('"experimentId":' + $id)) ("got " + $r.Status)

# 7. Wrong input must give clean errors, not 500.
$r = Call-Api "GET" "$ExperimentUrl/api/experiments/history?sort=password,asc"
Show-Result "unknown sort field (400)" ($r.Status -eq 400) ("got " + $r.Status)
$r = Call-Api "POST" "$ExperimentUrl/api/experiments/createExperiment" (@{ experimentName = "bad"; startTime = "not-a-time" } | ConvertTo-Json)
Show-Result "bad time text (400)" ($r.Status -eq 400) ("got " + $r.Status)
$r = Call-Api "POST" "$ExperimentUrl/api/experiments/createExperiment" (@{ experimentName = ("x" * 300) } | ConvertTo-Json)
Show-Result "name too long (400)" ($r.Status -eq 400) ("got " + $r.Status)
$r = Call-Api "GET" "$ExperimentUrl/api/experiments/999999999"
Show-Result "unknown id (404)" ($r.Status -eq 404) ("got " + $r.Status)

# 8. Delete it, then it must be gone.
$r = Call-Api "DELETE" "$ExperimentUrl/api/experiments/$id"
Show-Result "delete (200)" ($r.Status -eq 200) ("got " + $r.Status)
$r = Call-Api "GET" "$ExperimentUrl/api/experiments/$id"
Show-Result "get after delete (404)" ($r.Status -eq 404) ("got " + $r.Status)