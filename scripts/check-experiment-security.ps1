# =====================================================================
# check-experiment-security.ps1  -  a PowerShell script (.ps1 = a text file of PowerShell commands).
# It checks that experiment-service (port 8082) accepts tokens from auth-service (port 8081)
# and refuses everybody else. The password is asked with stars. It is never printed or saved.
#
# BEFORE you run it, START BOTH services (two PowerShell windows):
#     auth-service:        cd services\auth-service        then  .\mvnw.cmd spring-boot:run
#     experiment-service:  cd services\experiment-service  then  .\mvnw.cmd spring-boot:run
# Then, from the project root (the folder that contains "infra"), run:
#     .\scripts\check-experiment-security.ps1
# =====================================================================

param(
    [string]$AuthUrl       = "http://localhost:8081",
    [string]$ExperimentUrl = "http://localhost:8082",
    [string]$Username      = "developer"
)

$secure = Read-Host "Password for '$Username'" -AsSecureString
$plain  = [System.Net.NetworkCredential]::new("", $secure).Password

# Runs a web request and returns the status number, even for errors (401, 404 ...).
function Get-Status {
    param([scriptblock]$Call)
    try {
        $response = & $Call
        return [int]$response.StatusCode
    } catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        return -1
    }
}

function Show-Result {
    param([string]$Name, [bool]$Ok, $Detail)
    if ($Ok) { Write-Host ("PASS  " + $Name + "  " + $Detail) -ForegroundColor Green }
    else     { Write-Host ("FAIL  " + $Name + "  " + $Detail) -ForegroundColor Red }
}

# 1. Health must be open (Kubernetes needs it).
$code = Get-Status { Invoke-WebRequest -Uri "$ExperimentUrl/actuator/health" -UseBasicParsing }
Show-Result "health is open (200)" ($code -eq 200) "got $code"

# 2. A protected URL with NO token must say 401.
$code = Get-Status { Invoke-WebRequest -Uri "$ExperimentUrl/api/experiments/" -UseBasicParsing }
Show-Result "no token (401)" ($code -eq 401) "got $code"

# 3. A FAKE token must say 401.
$code = Get-Status { Invoke-WebRequest -Uri "$ExperimentUrl/api/experiments/" -Headers @{ Authorization = "Bearer abc.def.ghi" } -UseBasicParsing }
Show-Result "fake token (401)" ($code -eq 401) "got $code"

# 4. Log in to auth-service to get the cookie (the session keeps it, like a browser).
$body = @{ username = $Username; password = $plain } | ConvertTo-Json
try {
    $login = Invoke-WebRequest -Uri "$AuthUrl/api/auth/login" -Method Post -ContentType "application/json" -Body $body -SessionVariable session -UseBasicParsing
    Show-Result "login to auth-service (200)" ($login.StatusCode -eq 200) ""
} catch {
    Show-Result "login to auth-service" $false $_.Exception.Message
    return
}

# 5. Copy the cookie to the experiment-service address.
#    (Browsers do this by themselves: cookies are shared between ports on localhost.
#     PowerShell needs a hint, so we add the same cookie for the other URL.)
foreach ($c in $session.Cookies.GetCookies([uri]$AuthUrl)) {
    $session.Cookies.Add([uri]$ExperimentUrl, (New-Object System.Net.Cookie($c.Name, $c.Value, "/", "localhost")))
}

# 6. With the token, the door opens. There is no controller yet, so the answer is 404
#    (Not Found). 404 here means: the token was ACCEPTED and no page exists yet.
$code = Get-Status { Invoke-WebRequest -Uri "$ExperimentUrl/api/experiments/" -WebSession $session -UseBasicParsing }
Show-Result "real token accepted (404 for now)" ($code -eq 404) "got $code"