# GO-LIVE RUNBOOK – FlexSim platform (production)

A **runbook** = a step-by-step checklist. Anyone in the team can follow it and get the same result.
Tick each box in order. Do NOT skip the "approval" steps.

---

## Part 0 – Ask the company first (nothing works without these)

| # | What to ask | Why we need it |
|---|-------------|----------------|
| 1 | Azure subscription for prod + permission (Contributor + User Access Administrator on one resource group) | Terraform creates everything there |
| 2 | Allowed region + allowed VM sizes + CPU quota | Same SKU/quota problems we had in dev |
| 3 | A network range (example 10.20.0.0/16) that does not clash with company networks | VNet in `prod.tfvars` |
| 4 | Domain name (example flexsim.company.com) + who edits DNS | HTTPS address of the app |
| 5 | Company certificate OR permission to use Let's Encrypt | HTTPS lock |
| 6 | FlexSim licence + installer + model file | Worker VM runs the simulation |
| 7 | Who approves prod deployments | GitHub "prod" required reviewer |
| 8 | Data rules (can real PostNord data go to Azure Sweden? how long to keep it?) | GDPR / company policy |
| 9 | Team email for alerts | `alert_email` |

---

## Part 1 – One-time setup (company subscription)

- [ ] `az login --tenant <company-tenant-id>` then `az account show` → it MUST show the company prod subscription.
- [ ] Create the state storage (never destroyed): resource group `<rg-flexsim-prod-tfstate>`, storage account, container `tfstate` (same commands as dev).
- [ ] Create the GitHub identity `id-flexsim-prod-github` in that resource group.
- [ ] Fill every `<...>` in `infra\terraform\environments\prod.tfvars` and `prod.backend.hcl`.

## Part 2 – Build the Azure resources (Terraform)

```
cd infra\terraform
terraform init -reconfigure -backend-config="environments\prod.backend.hcl"
terraform plan  -var-file="environments\prod.tfvars" -out prod.plan
```
- [ ] Send the plan to the approver. Only after "OK":
```
terraform apply prod.plan
```
- [ ] Write down the outputs (`terraform output`): ACR name, Key Vault name, Postgres server, Event Hubs, ALB id, gateway address.

## Part 3 – Put the cluster add-ons in place

- [ ] Connect: `az aks get-credentials -g <prod-rg> -n <prod-aks>` → `kubectl config current-context` must show the PROD cluster.
- [ ] Create the database users: `infra\scripts\create-db-users.ps1` (with prod names).
- [ ] Install the ALB controller (same command as dev, prod identity).
- [ ] Install cert-manager with Gateway API support:
```
helm repo add jetstack https://charts.jetstack.io
helm upgrade --install cert-manager jetstack/cert-manager -n cert-manager --create-namespace --set crds.enabled=true --set config.enableGatewayAPI=true
```

## Part 4 – HTTPS front door

- [ ] Fill `<PROD-ALB-ID>`, `<PROD-DOMAIN>`, `<TEAM-EMAIL>` in `infra\k8s\azure-prod\gateway.yaml` and `certificate.yaml`.
- [ ] `kubectl apply -f infra\k8s\azure-prod\gateway.yaml`
- [ ] Company IT: DNS **CNAME** `<PROD-DOMAIN>` → gateway address (Terraform output). Check: `nslookup <PROD-DOMAIN>`.
- [ ] `kubectl apply -f infra\k8s\azure-prod\certificate.yaml` → wait until `kubectl -n flexsim get certificate` shows **READY True**.

## Part 5 – GitHub "prod" environment

- [ ] GitHub → Settings → Environments → New → `prod`.
- [ ] Required reviewers = the approver. Deployment branches = `main` only.
- [ ] Variables: `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`, `PROD_RESOURCE_GROUP`, `PROD_AKS_NAME`, `PROD_ACR_NAME`, `PROD_APP_IDENTITY`.
- [ ] Fill `<...>` in `infra\helm\values\azure-prod\*.yaml` with the Terraform outputs and the domain.

## Part 6 – Deploy the 5 services

- [ ] GitHub → Actions → **deploy-prod** → Run workflow → choose service + commit SHA (the one tested in dev).
- [ ] Approver clicks Approve. Repeat for all 5 services (auth first).
- [ ] `kubectl -n flexsim get pods` → 10 pods (2 per service) Running.

## Part 7 – Worker VM (FlexSim)

- [ ] No public IP in prod. Connect with Azure Bastion or company VPN.
- [ ] Install Java 21 + FlexSim (licence) + copy jar, WinSW files, `.env` (prod values, `https://<PROD-DOMAIN>`).
- [ ] Start the "FlexSim Worker" service → it logs in and waits for jobs.

## Part 8 – Safety checks BEFORE telling users "it's live"

- [ ] `https://<PROD-DOMAIN>` shows the lock; `http://` redirects to `https://`.
- [ ] Login works; upload test files; one simulation runs end-to-end.
- [ ] Backup restore test: `infra\scripts\test-db-restore.ps1 -ResourceGroup <prod-rg> -Server <prod-server>` → PASSED. Write down the minutes.
- [ ] Rotate all secrets that were ever shown/leaked in dev: `infra\scripts\rotate-secrets.ps1` (prod parameters).
- [ ] Alerts: you received the action-group test email.
- [ ] secret-scan workflow is green.

## Part 9 – If something goes wrong after go-live (rollback)

- One service is broken: `helm rollback <service> -n flexsim` (goes back to the previous version in seconds).
  The deploy already uses `--atomic`, so a failed deploy rolls back by itself.
- Data was damaged: restore Postgres to a point in time before the problem (same command as the restore test, but to a new server and then switch `POSTGRES_HOST`).
- A secret leaked: `rotate-secrets.ps1`.

## Never do in prod
- Never `terraform destroy` prod. Never touch the tfstate resource group.
- Never apply without a reviewed plan.
- Never put passwords in Git, screenshots or chat.
