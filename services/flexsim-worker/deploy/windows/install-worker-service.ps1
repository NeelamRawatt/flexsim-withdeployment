# =====================================================================
# install-worker-service.ps1  (a .ps1 file = a PowerShell script: a list of Windows commands)
#
# Installs the flexsim-worker as a Windows SERVICE on the Azure VM. Run it ONCE,
# as Administrator, from the folder that holds these files (for example C:\flexsim-worker):
#
#   flexsim-worker.jar   the worker (from the CI build, artifact "flexsim-worker-package")
#   flexsim-worker.xml   the service settings (this folder)
#   flexsim-worker.exe   WinSW-x64.exe v2.12.0 from github.com/winsw/winsw/releases, RENAMED to this name
#   .env                 the secrets and addresses (copy .env.example, fill it in)
#
#   .\install-worker-service.ps1 -ServiceAccount "flexsimsvc"
#
# What it does:
#   1. checks that all files and Java 21 are there
#   2. LOCKS the .env file: only Administrators, SYSTEM and the service account may read it
#   3. installs the service (it does NOT start it yet)
# It never prints a password.
# =====================================================================
param(
    # The Windows user the service runs as. FlexSim and its licence must work for this user.
    [Parameter(Mandatory = $true)]
    [string]$ServiceAccount
)

$ErrorActionPreference = "Stop"   # stop at the first error
$here = $PSScriptRoot

# ---- 1. checks ----
foreach ($file in "flexsim-worker.jar", "flexsim-worker.xml", "flexsim-worker.exe", ".env") {
    if (-not (Test-Path (Join-Path $here $file))) {
        throw "Missing $file in $here"
    }
}
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    throw "JAVA_HOME is not set. Install Java 21 (Temurin) and tick 'Set JAVA_HOME'."
}

# ---- 2. lock the secrets file ----
# /inheritance:r = remove the rights copied from the folder; /grant:r = set exactly these rights.
$envFile = Join-Path $here ".env"
icacls $envFile /inheritance:r /grant:r "Administrators:F" "SYSTEM:F" "${ServiceAccount}:R" | Out-Null
Write-Host "Locked .env: only Administrators, SYSTEM and $ServiceAccount can read it."

# ---- 3. install the service ----
& (Join-Path $here "flexsim-worker.exe") install
Write-Host ""
Write-Host "Service 'FlexSim Worker' installed. Two things left, by hand:"
Write-Host "  a) services.msc -> FlexSim Worker -> Log On -> 'This account' = $ServiceAccount (type its password there)"
Write-Host "  b) Start-Service flexsim-worker   then check: Get-Content .\logs\flexsim-worker.out.log -Tail 30"
