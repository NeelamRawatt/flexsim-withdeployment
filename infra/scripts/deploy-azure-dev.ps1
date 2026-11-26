# =====================================================================
# deploy-azure-dev.ps1   (folder: infra\scripts)
# Deploys the 5 FlexSim services to AKS (dev) with the SAME Helm chart as the laptop,
# using values\azure-dev\<service>.yaml.
#
# Values that change after every "terraform destroy/apply" are NOT in the values files;
# this script reads them from the Terraform outputs and passes them with --set:
#   azureWorkloadIdentity.clientId  <- app_identity_client_id
#   keyVault.tenantId               <- tenant_id
#
# Order: auth -> experiment -> file -> simulation -> kpi, ONE AT A TIME (--wait),
# because several Java apps starting together on one small node start slowly.
#
# Before: terraform apply  +  create-db-users.ps1  +  images pushed to ACR.
# Run (from the project root):
#   powershell -ExecutionPolicy Bypass -File infra\scripts\deploy-azure-dev.ps1
# =====================================================================

$ErrorActionPreference = "Stop"

$ResourceGroup = "rg-flexsim-dev"
$AksName       = "aks-flexsim-dev"
$Namespace     = "flexsim"
$Services      = "auth-service", "experiment-service", "file-service", "simulation-service", "kpi-service"
$Root          = Resolve-Path (Join-Path $PSScriptRoot "..\..")      # project root
$Chart         = Join-Path $Root "infra\helm\flexsim-service"
$ValuesDir     = Join-Path $Root "infra\helm\values\azure-dev"
$TfDir         = Join-Path $Root "infra\terraform"

# ---- 1) Safety: kubectl must point at the AZURE cluster ----
$ctx = kubectl config current-context
if ($ctx -ne $AksName) {
    throw "kubectl is pointing at '$ctx', not '$AksName'. Run: az aks get-credentials --resource-group $ResourceGroup --name $AksName --overwrite-existing"
}
Write-Host "kubectl context: $ctx (OK)" -ForegroundColor Cyan

# ---- 2) Read the values that change after every rebuild (Terraform outputs) ----
$clientId = terraform "-chdir=$TfDir" output -raw app_identity_client_id
$tenantId = terraform "-chdir=$TfDir" output -raw tenant_id
if ([string]::IsNullOrWhiteSpace($clientId) -or [string]::IsNullOrWhiteSpace($tenantId)) {
    throw "Could not read Terraform outputs. Run 'terraform apply' in infra\terraform first."
}
Write-Host "workload identity client id + tenant id read from Terraform (OK)" -ForegroundColor Cyan

# ---- 3) Namespace (create if missing) ----
kubectl create namespace $Namespace --dry-run=client -o yaml | kubectl apply -f - | Out-Null

# ---- 4) Deploy each service, one at a time ----
foreach ($svc in $Services) {
    Write-Host "===== $svc =====" -ForegroundColor Cyan
    helm upgrade --install $svc $Chart `
        --namespace $Namespace `
        -f (Join-Path $ValuesDir "$svc.yaml") `
        --set "azureWorkloadIdentity.clientId=$clientId" `
        --set "keyVault.tenantId=$tenantId" `
        --set "strategy.rollingUpdate.maxSurge=0" `
        --set "strategy.rollingUpdate.maxUnavailable=1" `
        --wait --timeout 10m
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Deploy of $svc failed. Look at: kubectl -n $Namespace get pods ; kubectl -n $Namespace describe pod <name> ; kubectl -n $Namespace logs deployment/$svc" -ForegroundColor Red
        throw "helm upgrade failed for $svc"
    }
}

# ---- 5) Show the result ----
Write-Host "===== result =====" -ForegroundColor Green
helm list -n $Namespace
kubectl -n $Namespace get pods
