# =====================================================================
# create-db-users.ps1   (folder: infra\scripts)
# Creates the 6 service database users (auth_user, experiment_user, ...) in the
# PRIVATE Azure PostgreSQL. Run it after every "terraform apply" (a new DB is empty).
#
# Why from inside AKS? The database has no internet address, so the laptop cannot
# reach it. A short-lived Kubernetes Job inside AKS can.
#
# What it does:
#   PART 1  safety checks (right cluster, tools logged in)
#   PART 2  read the passwords from Key Vault (never printed)
#   PART 3  give them to AKS as a TEMPORARY Kubernetes Secret
#   PART 4  run the one-time Job (db-setup-job.yaml), show its result
#   PART 5  clean up: delete the Job and the temporary Secret
#
# Run (from the project root):
#   powershell -ExecutionPolicy Bypass -File infra\scripts\create-db-users.ps1
# Safe to run again (idempotent): existing users just get their password updated.
# =====================================================================

$ErrorActionPreference = "Stop"   # stop at the first error

# ---- Names (same as Terraform dev.tfvars) ----
$ResourceGroup = "rg-flexsim-dev"
$KeyVault      = "kv-flexsim-dev-neelam"
$PgServer      = "psql-flexsim-dev-neelam"
$AksName       = "aks-flexsim-dev"
$Namespace     = "flexsim"
$AdminUser     = "flexsimadmin"
$Services      = "auth", "experiment", "file", "simulation", "kpi", "ai"
$JobFile       = Join-Path $PSScriptRoot "db-setup-job.yaml"

# ---------------------------------------------------------------------
# PART 1: safety checks
# ---------------------------------------------------------------------
Write-Host "PART 1: checks" -ForegroundColor Cyan

# kubectl must point at the AZURE cluster, never at docker-desktop or another cluster.
$ctx = kubectl config current-context
if ($ctx -ne $AksName) {
    throw "kubectl is pointing at '$ctx', not '$AksName'. Run: az aks get-credentials --resource-group $ResourceGroup --name $AksName"
}
Write-Host "  kubectl context: $ctx (OK)"

# Azure CLI must be logged in to the subscription that has our Key Vault.
az keyvault show --name $KeyVault --query name -o tsv | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Cannot see Key Vault '$KeyVault'. Run 'az login' / 'az account show'." }
Write-Host "  Key Vault reachable: $KeyVault (OK)"

# ---------------------------------------------------------------------
# PART 2: read the passwords from Key Vault (kept in memory only, never printed)
# ---------------------------------------------------------------------
Write-Host "PART 2: reading passwords from Key Vault" -ForegroundColor Cyan

function Get-KvSecret([string]$Name) {
    $value = az keyvault secret show --vault-name $KeyVault --name $Name --query value -o tsv
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($value)) { throw "Secret '$Name' not found in $KeyVault" }
    return $value
}

$pgHost = az postgres flexible-server show --resource-group $ResourceGroup --name $PgServer --query fullyQualifiedDomainName -o tsv
if ([string]::IsNullOrWhiteSpace($pgHost)) { throw "PostgreSQL server '$PgServer' not found" }

$secretArgs = @(
    "--from-literal=PGHOST=$pgHost",
    "--from-literal=PGUSER=$AdminUser",
    "--from-literal=PGPASSWORD=$(Get-KvSecret 'postgres-admin-password')"
)
foreach ($s in $Services) {
    # e.g. AUTH_PASS = value of "auth-db-password"
    $secretArgs += "--from-literal=$($s.ToUpper())_PASS=$(Get-KvSecret "$s-db-password")"
}
Write-Host "  read admin password + $($Services.Count) service passwords (not shown)"

try {
    # -----------------------------------------------------------------
    # PART 3: hand them to AKS as a TEMPORARY secret
    # -----------------------------------------------------------------
    Write-Host "PART 3: temporary Kubernetes secret" -ForegroundColor Cyan
    # Create the namespace if it does not exist yet ("dry-run + apply" = create-or-keep).
    kubectl create namespace $Namespace --dry-run=client -o yaml | kubectl apply -f - | Out-Null
    kubectl -n $Namespace delete secret db-setup-secret --ignore-not-found | Out-Null
    kubectl -n $Namespace create secret generic db-setup-secret @secretArgs | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not create the temporary secret" }
    Write-Host "  secret db-setup-secret created"

    # -----------------------------------------------------------------
    # PART 4: run the one-time Job inside AKS and show what it printed
    # -----------------------------------------------------------------
    Write-Host "PART 4: running the db-setup job (about 1 minute)" -ForegroundColor Cyan
    kubectl -n $Namespace delete job db-setup --ignore-not-found | Out-Null
    kubectl apply -f $JobFile | Out-Null
    kubectl -n $Namespace wait --for=condition=complete job/db-setup --timeout=180s | Out-Null
    $ok = ($LASTEXITCODE -eq 0)
    Write-Host "----- job output -----"
    kubectl -n $Namespace logs job/db-setup
    Write-Host "----------------------"
    if (-not $ok) { throw "The db-setup job did not finish successfully (see the output above)." }
    Write-Host "DONE: 6 database users are ready." -ForegroundColor Green
}
finally {
    # -----------------------------------------------------------------
    # PART 5: always clean up, even if something failed
    # -----------------------------------------------------------------
    Write-Host "PART 5: cleaning up" -ForegroundColor Cyan
    kubectl -n $Namespace delete job db-setup --ignore-not-found | Out-Null
    kubectl -n $Namespace delete secret db-setup-secret --ignore-not-found | Out-Null
    Write-Host "  temporary job and secret deleted"
}
