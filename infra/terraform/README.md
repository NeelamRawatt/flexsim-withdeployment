# Terraform — FlexSim Azure infrastructure

(folder: infra\terraform)

Terraform builds the Azure "house" for FlexSim. Helm (infra\helm) later puts the apps inside AKS.

## What it creates (dev = personal practice account, Sweden Central)

| File | Topic | Resources | Cost while it exists |
|---|---|---|---|
| main.tf | base | resource group `rg-flexsim-dev`, shared tags | Rs 0 |
| network.tf | private network | VNet `vnet-flexsim-dev` 10.10.0.0/16 + subnets snet-aks, snet-postgres (delegated), snet-gateway (delegated), snet-worker | Rs 0 |
| registry.tf | Docker images | ACR `acrflexsimdevneelam` (Basic, admin user OFF) | ~Rs 15/day |
| keyvault.tf | passwords | Key Vault `kv-flexsim-dev-neelam` (RBAC) + my role "Key Vault Secrets Officer" | ~Rs 0 |
| database.tf | PostgreSQL 16 | random admin password -> Key Vault, private DNS zone + VNet link, server `psql-flexsim-dev-neelam` (private, B1ms), 6 databases | ~Rs 45/day |
| eventhubs.tf | Kafka | namespace `evh-flexsim-dev-neelam` (Standard, 1 TU), 9 topics, send+listen key -> Key Vault | ~Rs 60/day |
| aks.tf | Kubernetes | AKS `aks-flexsim-dev` (Free tier, 1 x Standard_D2s_v6, Azure CNI, workload identity, Key Vault add-on) + AcrPull + Network Contributor | ~Rs 200/day |

Shared files: versions.tf (versions), providers.tf (which subscription), variables.tf (blanks),
outputs.tf (printed values), backend.tf (state in Azure), environments\dev.tfvars (answers),
environments\dev.backend.hcl (where the dev state lives).

TOTAL while everything runs: about Rs 320/day -> DESTROY after practice.

## One-time setup (already done — only for a NEW machine/account)

State storage is created with az CLI in its own resource group, which `terraform destroy` never touches:

```
az group create --name rg-flexsim-tfstate --location swedencentral --tags project=flexsim purpose=terraform-state managed-by=az-cli
az storage account create --name stflexsimtfneelam --resource-group rg-flexsim-tfstate --location swedencentral --sku Standard_LRS --kind StorageV2 --min-tls-version TLS1_2 --allow-blob-public-access false
$me = az ad signed-in-user show --query id -o tsv
$st = az storage account show --name stflexsimtfneelam --resource-group rg-flexsim-tfstate --query id -o tsv
az role assignment create --assignee-object-id $me --assignee-principal-type User --role "Storage Blob Data Contributor" --scope $st
az storage container create --name tfstate --account-name stflexsimtfneelam --auth-mode login
```

On a new laptop, connect to the existing state:

```
cd C:\Users\neela\Documents\flexsim-project\infra\terraform
terraform init -backend-config="environments\dev.backend.hcl"
```

## Daily routine (practice account)

```
az account show --output table                       # 1. right subscription? (personal!)
terraform plan    -var-file="environments\dev.tfvars" # 2. look; read the summary line
terraform apply   -var-file="environments\dev.tfvars" # 3. build (type yes) ~10-15 min
...practise...
terraform destroy -var-file="environments\dev.tfvars" # 4. delete everything (type yes) when done
```

Connect kubectl to AKS (after apply), and switch back to the laptop afterwards:

```
az aks get-credentials --resource-group rg-flexsim-dev --name aks-flexsim-dev
kubectl config get-contexts
kubectl get nodes
kubectl config use-context docker-desktop
```

## Rules

- ALWAYS `az account show` before plan/apply/destroy. Never run against the company subscription without approval.
- Read the plan summary. Unexpected "to destroy" -> STOP.
- Never commit `*.tfstate` or `.terraform\` (see .gitignore). State can contain passwords.
- Never change Terraform-managed resources by hand in the portal (drift).
- Don't touch the automatic `MC_rg-flexsim-dev_...` resource group (AKS owns it).
- A new provider in versions.tf -> run `terraform init` again.

## Problems we hit (and the fix)

| Error | Meaning | Fix |
|---|---|---|
| `SkuNotAvailable` (PostgreSQL, centralindia) | size blocked for this new account in that region | moved everything to swedencentral (one line in dev.tfvars) |
| `VM size ... not allowed in your subscription` (AKS) | node size blocked; error lists allowed sizes | picked an allowed size with the same CPU/RAM |
| `ErrCode_InsufficientVCPUQuota` | 0 vCPU quota for that VM family | `az vm list-usage` -> chose Standard_D2s_v6 (Dsv6 family quota 10) |
| `zone can only be changed...` (PostgreSQL) | Azure picked an availability zone itself | `lifecycle { ignore_changes = [zone, ...] }` |
| `403 Forbidden` on a Key Vault secret / storage | new RBAC role not active yet (propagation) | wait 2-5 min, run again |
| destroy stuck / leftover resource not in state | failed create left a half-made resource | practice only: `az group delete --name rg-flexsim-dev --yes`, then apply |
| plan wants to remove `upgrade_settings` | Azure added defaults | wrote the same values in aks.tf |

## Later: company subscription (prod)

Same code + `environments\prod.tfvars` + `environments\prod.backend.hcl` (key = flexsim-prod.tfstate).
Expect: company tags/regions/policies, purge protection = true, 2+ AKS nodes, approval of the plan before apply.
