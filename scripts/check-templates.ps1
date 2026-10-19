# =====================================================================
# check-templates.ps1  -  a PowerShell script (.ps1 = a text file of PowerShell commands).
# It tests the template URLs (/api/templates) and the chute list (/api/chute-master) of experiment-service.
# It saves ONE test template and deletes it at the end. The password is asked with stars,
# never printed or saved.
#
# BEFORE you run it, start BOTH services (two windows):
#     auth-service        (port 8081)   .\mvnw.cmd spring-boot:run
#     experiment-service  (port 8082)   .\mvnw.cmd spring-boot:run
# Then, from the project root (the folder that contains "infra"):
#     .\scripts\check-templates.ps1
# The user must be ADMIN or DEVELOPER (the default 'developer' user is).
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

# Turns a list into JSON and keeps it a list even when it has one item.
function To-Json { param($obj) return (ConvertTo-Json -InputObject $obj -Depth 6) }

# 1. Log in, then give the same cookie to experiment-service.
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
$t = "$ExperimentUrl/api/templates"

# 2. Without a login the URLs must say 401.
$anon = try { (Invoke-WebRequest -Uri $t -UseBasicParsing).StatusCode } catch { [int]$_.Exception.Response.StatusCode }
Show-Result "no login gives 401" ($anon -eq 401) ("got " + $anon)

# 3. Chute list (filled by the Flyway file V2). 97 chutes in total.
$r = Call-Api "GET" "$ExperimentUrl/api/chute-master/sortname-suggestions?query=201"
$n = ([regex]::Matches($r.Text, '"sortName"')).Count
Show-Result "chute query 201 gives 13 cages" ($r.Status -eq 200 -and $n -eq 13 -and $r.Text -match '"sortName":"20101"' -and $r.Text -match '"sortName":"20113"') ("got " + $r.Status + ", " + $n + " items")
$r = Call-Api "GET" "$ExperimentUrl/api/chute-master/sortname-suggestions?query=40200"
Show-Result "drop chute 402 has cage 0" ($r.Status -eq 200 -and $r.Text -match '"chuteType":"DROP"' -and $r.Text -match '"cageId":0') ("got " + $r.Status)
$r = Call-Api "GET" "$ExperimentUrl/api/chute-master/sortname-suggestions?limit=5"
Show-Result "limit=5 gives 5 items" ($r.Status -eq 200 -and ([regex]::Matches($r.Text, '"sortName"')).Count -eq 5) ("got " + $r.Status)
$r = Call-Api "GET" "$ExperimentUrl/api/chute-master/sortname-suggestions?query=abc999999"
Show-Result "no match gives empty list" ($r.Status -eq 200 -and $r.Text.Trim() -eq "[]") ("got " + $r.Status)

# 4. Save an INFEED template (2 rows).
$good = @{
    type = "infeed"; templateName = "check-template"; fileName = "check.xlsx"
    data = @(
        @{ shiftId = "1"; tc = "TCA"; resourceCount = 3 },
        @{ shiftId = "2"; tc = "TCB"; resourceCount = "4" }
    )
}
$r = Call-Api "POST" $t (To-Json $good)
$id = [regex]::Match($r.Text, '"id":(\d+)').Groups[1].Value
Show-Result "save infeed template (200)" ($r.Status -eq 200 -and $id -ne "" -and $r.Text -match '"type":"infeed"' -and $r.Text -match '"rowCount":2') ("got " + $r.Status + " id=" + $id)
if ($id -eq "") { return }

# 5. It must be in the list, with its rows in order.
$r = Call-Api "GET" $t
Show-Result "list has infeed / zone / sortPlan" ($r.Status -eq 200 -and $r.Text -match '"infeed":\[' -and $r.Text -match '"zone":\[' -and $r.Text -match '"sortPlan":\[') ("got " + $r.Status)
Show-Result "list contains our template with 2 data rows" ($r.Text -match ('"id":' + $id + ',"name":"check-template"') -and $r.Text -match '"tc":"TCA"' -and $r.Text -match '"tc":"TCB"') ""
Show-Result "rows keep their order (TCA before TCB)" ($r.Text.IndexOf('"tc":"TCA"') -lt $r.Text.IndexOf('"tc":"TCB"')) ""

# 6. Zone and sort plan templates (valid), then bad input must give clean 400 errors.
$zone = @{ type = "zone"; data = @(@{ shiftId = "1"; zoneId = "Z1"; chuteIdRaw = "1,2,3" }) }
$r = Call-Api "POST" $t (To-Json $zone)
$zoneId = [regex]::Match($r.Text, '"id":(\d+)').Groups[1].Value
Show-Result "save zone template, default name" ($r.Status -eq 200 -and $r.Text -match '"name":"Zone Template"') ("got " + $r.Status)
$plan = @{ type = "sortPlan"; data = @(@{ oldSortName = "A"; newSortName = "B" }) }
$r = Call-Api "POST" $t (To-Json $plan)
$planId = [regex]::Match($r.Text, '"id":(\d+)').Groups[1].Value
Show-Result "save sort plan template (type 'sortPlan')" ($r.Status -eq 200 -and $r.Text -match '"type":"sort_plan"') ("got " + $r.Status)

$r = Call-Api "POST" $t (To-Json @{ type = "pizza"; data = @(@{ a = 1 }) })
Show-Result "unknown type (400)" ($r.Status -eq 400) ("got " + $r.Status)
$r = Call-Api "POST" $t (To-Json @{ type = "infeed"; data = @() })
Show-Result "empty data (400)" ($r.Status -eq 400) ("got " + $r.Status)
$r = Call-Api "POST" $t (To-Json @{ type = "infeed"; data = @(@{ shiftId = "1"; tc = "TCA"; resourceCount = -2 }) })
Show-Result "negative resourceCount (400)" ($r.Status -eq 400) ("got " + $r.Status)
$r = Call-Api "POST" $t (To-Json @{ type = "infeed"; data = @(@{ shiftId = "1"; resourceCount = 2 }) })
Show-Result "infeed row without tc (400)" ($r.Status -eq 400) ("got " + $r.Status)
$r = Call-Api "POST" $t (To-Json @{ type = "zone"; data = @(@{ shiftId = "1"; zoneId = "Z1" }) })
Show-Result "zone row without chuteIdRaw (400)" ($r.Status -eq 400) ("got " + $r.Status)
$r = Call-Api "POST" $t (To-Json @{ type = "sort_plan"; data = @(@{ oldSortName = "A" }) })
Show-Result "sort plan row without newSortName (400)" ($r.Status -eq 400) ("got " + $r.Status)
$r = Call-Api "POST" $t (To-Json @{ type = "infeed"; templateName = ("x" * 300); data = @(@{ shiftId = "1"; tc = "T"; resourceCount = 1 }) })
Show-Result "template name too long (400)" ($r.Status -eq 400) ("got " + $r.Status)

# 7. Delete. First the rows are counted in the database (optional, needs Docker).
$rowsBefore = (& docker exec flexsim-postgres psql -U postgres -d experiment_db -t -A -c "select count(*) from resource_template_row where template_id=$id" 2>$null)
$r = Call-Api "DELETE" "$t/$id"
Show-Result "delete (204)" ($r.Status -eq 204) ("got " + $r.Status)
$r = Call-Api "DELETE" "$t/$id"
Show-Result "delete again (404)" ($r.Status -eq 404) ("got " + $r.Status)
$r = Call-Api "DELETE" "$t/999999999"
Show-Result "delete unknown id (404)" ($r.Status -eq 404) ("got " + $r.Status)
if ($LASTEXITCODE -eq 0 -and $rowsBefore) {
    $rowsAfter = (& docker exec flexsim-postgres psql -U postgres -d experiment_db -t -A -c "select count(*) from resource_template_row where template_id=$id" 2>$null)
    Show-Result "cascade: rows removed by the database" (("$rowsBefore".Trim() -eq "2") -and ("$rowsAfter".Trim() -eq "0")) ("before=" + "$rowsBefore".Trim() + " after=" + "$rowsAfter".Trim())
} else {
    Write-Host "SKIP  cascade check (docker command did not run; not a problem)" -ForegroundColor Yellow
}
$r = Call-Api "GET" $t
Show-Result "deleted template is gone from the list" ($r.Status -eq 200 -and $r.Text -notmatch ('"id":' + $id + ',')) ""

# 8. Clean up the other two test templates.
if ($zoneId -ne "") { Call-Api "DELETE" "$t/$zoneId" | Out-Null }
if ($planId -ne "") { Call-Api "DELETE" "$t/$planId" | Out-Null }
Write-Host "Done. Test templates were removed." -ForegroundColor Cyan