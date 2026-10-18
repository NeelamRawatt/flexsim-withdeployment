# =====================================================================
# login-test.ps1  -  a PowerShell script (.ps1 = a text file of PowerShell commands).
# It checks that auth-service login works. It asks for the password with
# stars (the password is never printed and never saved).
#
# How to run (from the project root, the folder that contains "infra"):
#     .\scripts\login-test.ps1
# Auth-service must be running first (.\mvnw.cmd spring-boot:run).
# =====================================================================

param(
    [string]$BaseUrl  = "http://localhost:8081",
    [string]$Username = "developer"
)

# Ask for the password. -AsSecureString shows stars while you type.
$secure = Read-Host "Password for '$Username'" -AsSecureString
$plain  = [System.Net.NetworkCredential]::new("", $secure).Password

# Small helper: runs a web request and returns the status code (even for errors).
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

# JSON is built by PowerShell, so we never write quotes by hand.
$goodBody = @{ username = $Username; password = $plain }    | ConvertTo-Json
$badBody  = @{ username = $Username; password = "wrong-password-123" } | ConvertTo-Json

# 1. Health page must be open to everybody (Kubernetes needs it).
$code = Get-Status { Invoke-WebRequest -Uri "$BaseUrl/actuator/health" -UseBasicParsing }
Show-Result "health is open (200)" ($code -eq 200) "got $code"

# 2. A protected page without a login must say 401.
$code = Get-Status { Invoke-WebRequest -Uri "$BaseUrl/api/auth/me" -UseBasicParsing }
Show-Result "/me without login (401)" ($code -eq 401) "got $code"

# 3. A wrong password must say 401.
$code = Get-Status { Invoke-WebRequest -Uri "$BaseUrl/api/auth/login" -Method Post -ContentType "application/json" -Body $badBody -UseBasicParsing }
Show-Result "wrong password (401)" ($code -eq 401) "got $code"

# 4. The right password must say 200 and set two cookies.
#    -SessionVariable keeps the cookies in $session, like a browser does.
try {
    $login = Invoke-WebRequest -Uri "$BaseUrl/api/auth/login" -Method Post -ContentType "application/json" -Body $goodBody -SessionVariable session -UseBasicParsing
    Show-Result "login (200)" ($login.StatusCode -eq 200) $login.Content

    $cookies = $session.Cookies.GetCookies([uri]$BaseUrl)
    foreach ($c in $cookies) {
        Show-Result ("cookie " + $c.Name + " is HttpOnly") ($c.HttpOnly) ("HttpOnly=" + $c.HttpOnly)
    }

    # 5. With the cookies, "who am I?" must work.
    $me = Invoke-RestMethod -Uri "$BaseUrl/api/auth/me" -WebSession $session
    Show-Result "/me with cookies" ($me.username -eq $Username) ("user=" + $me.username + " role=" + $me.role)
} catch {
    Show-Result "login" $false $_.Exception.Message
}

# Forget the password in memory.
$plain = $null