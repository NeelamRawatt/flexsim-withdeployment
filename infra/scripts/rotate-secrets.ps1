# =====================================================================
# rotate-secrets.ps1   (folder: infra\scripts)
# SECRET ROTATION = replace passwords/keys with NEW ones (before go-live, after any leak,
# and on a schedule). Rotates, in this order:
#   1) Event Hubs app key (Kafka)            -> Azure makes a new key
#   2) DB passwords + JWT secret             -> Terraform makes new ones (secret_rotation_id)
#      + the Key Vault copies of everything  -> updated by the same terraform apply
#   3) the database users get the new passwords   (create-db-users.ps1)
#   4) all pods restart and read the new secrets   (kubectl rollout restart)
# BEFORE running: change secret_rotation_id in environments\dev.tfvars (e.g. to today's date).
# AFTER running: put the new kafka-sasl-jaas-config into the worker VM's .env and restart
# the "FlexSim Worker" service (the VM does not read Key Vault by itself).
# Note: JWT change = every user must log in again.
#
# Run (from the project root):
#   powershell -ExecutionPolicy Bypass -File infra\scripts\rotate-secrets.ps1
# =====================================================================
param(
    [string]$ResourceGroup = "rg-flexsim-dev",
    [string]$EventHubs     = "evh-flexsim-dev-neelam",
    [string]$VarFile       = "environments\dev.tfvars",
    [string]$Namespace     = "flexsim"
)
$ErrorActionPreference = "Stop"
$Root  = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$TfDir = Join-Path $Root "infra\terraform"

Write-Host "Have you changed secret_rotation_id in $VarFile ? (otherwise DB passwords/JWT do NOT change)" -ForegroundColor Yellow
if ((Read-Host "Type yes to continue") -ne "yes") { throw "Stopped." }

# 1) New Event Hubs key for our app's access rule (the old primary key stops working).
Write-Host "1) Renewing the Event Hubs key" -ForegroundColor Cyan
az eventhubs namespace authorization-rule keys renew -g $ResourceGroup --namespace-name $EventHubs `
    --name flexsim-app-send-listen --key PrimaryKey --output none
if ($LASTEXITCODE -ne 0) { throw "Event Hubs key renew failed" }

# 2) Terraform: new DB passwords + JWT (because secret_rotation_id changed) and it refreshes
#    the Event Hubs connection string -> all Key Vault secrets updated.
Write-Host "2) terraform apply (new passwords + Key Vault updates) - read the plan, then type yes" -ForegroundColor Cyan
terraform "-chdir=$TfDir" apply "-var-file=$VarFile"
if ($LASTEXITCODE -ne 0) { throw "terraform apply failed" }

# 3) Give the database users their new passwords.
Write-Host "3) Updating the database users" -ForegroundColor Cyan
& (Join-Path $PSScriptRoot "create-db-users.ps1")

# 4) Restart the services so they read the new secrets (rolling restart = no downtime with 2+ replicas).
Write-Host "4) Restarting the services" -ForegroundColor Cyan
foreach ($svc in "auth-service", "experiment-service", "file-service", "simulation-service", "kpi-service") {
    kubectl -n $Namespace rollout restart "deployment/$svc"
    kubectl -n $Namespace rollout status "deployment/$svc" --timeout=10m
}
Write-Host "DONE. Last step by hand: update kafka-sasl-jaas-config in the worker VM's .env and restart the FlexSim Worker service." -ForegroundColor Green
