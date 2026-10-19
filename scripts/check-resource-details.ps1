# =====================================================================
# check-resource-details.ps1  -  a PowerShell script (.ps1 = a text file of PowerShell commands).
# It tests the zone, infeed, sort plan and acceptable-parcel-unit URLs of experiment-service.
# It creates ONE test experiment and deletes it at the end. The password is asked with stars,
# never printed or saved.
#
# BEFORE you run it, start BOTH services (two windows):
#     auth-service        (port 8081)   .\mvnw.cmd spring-boot:run
#     experiment-service  (port 8082)   .\mvnw.cmd spring-boot:run
# Then, from the project root (the folder that contains "infra"):
#     .\scripts\check-resource-details.ps1
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

# Makes JSON from a LIST. "-InputObject @(...)" keeps it a list even when it has only one item.
function To-JsonList { param($items) return (ConvertTo-Json -InputObject @($items) -Depth 5) }

# 1. Log in to auth-service, then give the same cookie to experiment-service.
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

# 2. Make a test experiment.
$createBody = @{ experimentName = "check-details"; terminal = "TC1"; sortingType = "A"; useCaseId = 1; selectedDate = "2026-10-19" } | ConvertTo-Json
$r = Call-Api "POST" "$ExperimentUrl/api/experiments/createExperiment" $createBody
$id = [regex]::Match($r.Text, '"experimentId":(\d+)').Groups[1].Value
Show-Result "test experiment created" ($r.Status -eq 200 -and $id -ne "") ("id=" + $id)
if ($id -eq "") { return }
$expId = [int64]$id
$base = "$ExperimentUrl/api/resourceDetails"

# ---------------------------------------------------------------- ZONE
# Sent on purpose in a MIXED order. The answer must come back sorted by shift, zone, resource.
$zones = @(
    @{ experimentId = $expId; shiftId = 2; zoneId = "Z1"; resourceId = 1; chutes = "[7,8]" },
    @{ experimentId = $expId; shiftId = 1; zoneId = "Z2"; resourceId = 1; chutes = "9" },
    @{ experimentId = $expId; shiftId = 1; zoneId = "Z1"; resourceId = 2; chutes = "[4]" },
    @{ experimentId = $expId; shiftId = 1; zoneId = "Z1"; resourceId = 1; chutes = "[1,2,3]" }
)
$r = Call-Api "POST" "$base/setZoneDetails" (To-JsonList $zones)
Show-Result "zone save (200)" ($r.Status -eq 200 -and $r.Text -match "Saved") ("got " + $r.Status + " " + $r.Text)

$r = Call-Api "GET" "$base/getZoneDetails/$id"
Show-Result "zone read (200, 4 rows)" ($r.Status -eq 200 -and ([regex]::Matches($r.Text, '"zoneId"')).Count -eq 4) ("got " + $r.Status)
Show-Result "zone chuteIds [1,2,3] present" ($r.Text -match '"chuteIds":\[1,2,3\]') ""
# First item must be shift 1, zone Z1, resource 1 (chutes 1,2,3). Last must be shift 2.
$first = [regex]::Match($r.Text, '"chuteIds":\[([0-9,]*)\]').Groups[1].Value
$last  = [regex]::Matches($r.Text, '"chuteIds":\[([0-9,]*)\]')[3].Groups[1].Value
Show-Result "zone order is correct" ($first -eq "1,2,3" -and $last -eq "7,8") ("first=" + $first + " last=" + $last)

$r = Call-Api "GET" "$base/clone/getZoneDetails/$id"
Show-Result "zone clone URL works (200)" ($r.Status -eq 200 -and $r.Text -match '"chuteIds"') ("got " + $r.Status)

# REPLACE: saving again with 1 row must leave exactly 1 row (not 5).
$r = Call-Api "POST" "$base/setZoneDetails" (To-JsonList @(@{ experimentId = $expId; shiftId = 1; zoneId = "Z9"; resourceId = 1; chutes = "[5]" }))
$r = Call-Api "GET" "$base/getZoneDetails/$id"
Show-Result "zone save REPLACES old rows" ($r.Status -eq 200 -and ([regex]::Matches($r.Text, '"zoneId"')).Count -eq 1 -and $r.Text -match '"zoneId":"Z9"') ""

# Bad input must give clean 400 errors.
$r = Call-Api "POST" "$base/setZoneDetails" (To-JsonList @(@{ experimentId = $expId; shiftId = 1; zoneId = "Z1"; resourceId = 1; chutes = "[1,abc]" }))
Show-Result "bad chute text (400)" ($r.Status -eq 400) ("got " + $r.Status)
$r = Call-Api "POST" "$base/setZoneDetails" (To-JsonList @(
    @{ experimentId = $expId; shiftId = 1; zoneId = "Z1"; resourceId = 1; chutes = "[1]" },
    @{ experimentId = ($expId + 1); shiftId = 1; zoneId = "Z1"; resourceId = 2; chutes = "[2]" }))
Show-Result "mixed experimentIds (400)" ($r.Status -eq 400) ("got " + $r.Status)
$r = Call-Api "POST" "$base/setZoneDetails" (To-JsonList @(@{ shiftId = 1; zoneId = "Z1"; resourceId = 1; chutes = "[1]" }))
Show-Result "missing experimentId (400)" ($r.Status -eq 400) ("got " + $r.Status)
$r = Call-Api "POST" "$base/setZoneDetails" (To-JsonList @(@{ experimentId = 999999999; shiftId = 1; zoneId = "Z1"; resourceId = 1; chutes = "[1]" }))
Show-Result "unknown experiment on save (404)" ($r.Status -eq 404) ("got " + $r.Status)
$r = Call-Api "POST" "$base/setZoneDetails" "[]"
Show-Result "empty list is OK (200, Skipped)" ($r.Status -eq 200 -and $r.Text -match "Skipped") ("got " + $r.Status)
$r = Call-Api "GET" "$base/getZoneDetails/999999999"
Show-Result "unknown experiment on read (404)" ($r.Status -eq 404) ("got " + $r.Status)

# --------------------------------------------------------------- INFEED
$infeeds = @(
    @{ experimentId = $expId; shiftId = 2; zoneId = "Z1"; infeed = "I1"; tc = "TCB"; noOfResources = 3; active = $true },
    @{ experimentId = $expId; shiftId = 1; zoneId = "Z1"; infeed = "I2"; tc = "TCB"; noOfResources = 2; active = $true },
    @{ experimentId = $expId; shiftId = 1; zoneId = "Z1"; infeed = "I1"; tc = "TCA"; noOfResources = 5; active = $false }
)
$r = Call-Api "POST" "$base/setInfeedDetails" (To-JsonList $infeeds)
Show-Result "infeed save (200)" ($r.Status -eq 200 -and $r.Text -match "Saved") ("got " + $r.Status)
$r = Call-Api "GET" "$base/getInfeedDetails/$id"
Show-Result "infeed read (200, 3 rows)" ($r.Status -eq 200 -and ([regex]::Matches($r.Text, '"tc"')).Count -eq 3) ("got " + $r.Status)
# Order is shift, then tc: (1,TCA) (1,TCB) (2,TCB) -> the number of resources reads 5, 2, 3.
$counts = ([regex]::Matches($r.Text, '"noOfResources":(\d+)') | ForEach-Object { $_.Groups[1].Value }) -join ","
Show-Result "infeed order is correct" ($counts -eq "5,2,3") ("got " + $counts)
$r = Call-Api "GET" "$base/clone/getInfeedDetails/$id"
Show-Result "infeed clone URL works (200)" ($r.Status -eq 200) ("got " + $r.Status)

# ----------------------------------------------------------- SORT PLAN
$plans = @(
    @{ experimentId = $expId; oldSortName = "OLD-A"; newSortName = "NEW-A" },
    @{ experimentId = $expId; oldSortName = "OLD-B"; newSortName = "NEW-B" }
)
$r = Call-Api "POST" "$base/setSortPlanDetails" (To-JsonList $plans)
Show-Result "sort plan save (200)" ($r.Status -eq 200 -and $r.Text -match "Saved") ("got " + $r.Status)
$r = Call-Api "GET" "$base/getSortPlanDetails/$id"
Show-Result "sort plan read (200, 2 rows)" ($r.Status -eq 200 -and ([regex]::Matches($r.Text, '"oldSortName"')).Count -eq 2) ("got " + $r.Status)
$r = Call-Api "GET" "$base/clone/getSortPlanDetails/$id"
Show-Result "sort plan clone URL works (200)" ($r.Status -eq 200) ("got " + $r.Status)
$r = Call-Api "POST" "$base/setSortPlanDetails" (To-JsonList @(@{ experimentId = $expId; oldSortName = ("x" * 300); newSortName = "N" }))
Show-Result "text too long (400)" ($r.Status -eq 400) ("got " + $r.Status)

# --------------------------------------------- ACCEPTABLE PARCEL UNIT
$apu = "$ExperimentUrl/api/acceptable-parcel-unit"
$r = Call-Api "GET" "$apu/byExperiment/$id"
Show-Result "parcel limits: none yet (204)" ($r.Status -eq 204) ("got " + $r.Status)
$good = @{ experimentId = $expId; minHeight = 1; maxHeight = 50; minLength = 1; maxLength = 100; minWidth = 1; maxWidth = 80; minWeight = 0.5; maxWeight = 30 } | ConvertTo-Json
$r = Call-Api "POST" "$apu/save" $good
Show-Result "parcel limits save (200)" ($r.Status -eq 200 -and $r.Text -match "successfully") ("got " + $r.Status + " " + $r.Text)
$r = Call-Api "GET" "$apu/byExperiment/$id"
Show-Result "parcel limits read (200)" ($r.Status -eq 200 -and $r.Text -match '"maxWeight":30') ("got " + $r.Status)
$r = Call-Api "POST" "$apu/save" $good
$r = Call-Api "GET" "$apu/byExperiment/$id"
Show-Result "parcel limits REPLACE (still one row)" ($r.Status -eq 200 -and ([regex]::Matches($r.Text, '"minHeight"')).Count -eq 1) ""
$neg = @{ experimentId = $expId; minHeight = -1; maxHeight = 5 } | ConvertTo-Json
$r = Call-Api "POST" "$apu/save" $neg
Show-Result "negative number (400)" ($r.Status -eq 400) ("got " + $r.Status)
$swap = @{ experimentId = $expId; minHeight = 10; maxHeight = 5 } | ConvertTo-Json
$r = Call-Api "POST" "$apu/save" $swap
Show-Result "min above max (400)" ($r.Status -eq 400) ("got " + $r.Status)

# ------------------------------------------------ CASCADE (optional)
# Delete the experiment. The database must delete all child rows itself (ON DELETE CASCADE).
$r = Call-Api "DELETE" "$ExperimentUrl/api/experiments/$id"
Show-Result "delete test experiment (200)" ($r.Status -eq 200 -or $r.Status -eq 204) ("got " + $r.Status)
$sql = "select (select count(*) from zone_resource_detail where exp_id=$id) + (select count(*) from infeed_resource_detail where exp_id=$id) + (select count(*) from sort_plan_detail where exp_id=$id) + (select count(*) from acceptable_parcel_unit where exp_id=$id);"
$left = (& docker exec flexsim-postgres psql -U postgres -d experiment_db -t -A -c $sql 2>$null)
if ($LASTEXITCODE -eq 0 -and $left -ne $null) {
    Show-Result "cascade: no child rows left" ("$left".Trim() -eq "0") ("rows left=" + "$left".Trim())
} else {
    Write-Host "SKIP  cascade check (docker command did not run; not a problem)" -ForegroundColor Yellow
}
$r = Call-Api "GET" "$base/getZoneDetails/$id"
Show-Result "after delete, read gives 404" ($r.Status -eq 404) ("got " + $r.Status)