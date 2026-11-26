# =====================================================================
# prod.backend.hcl   (folder: infra\terraform\environments)
# WHERE the PRODUCTION state (receipt) is stored: in the COMPANY subscription,
# in its own never-destroyed resource group. Created ONCE (like dev) with az CLI.
# Used by: terraform init -reconfigure -backend-config="environments\prod.backend.hcl"
# (-reconfigure = switch this folder from the dev state to the prod state)
# =====================================================================

resource_group_name  = "<rg-flexsim-prod-tfstate>"
storage_account_name = "<stflexsimprodtfXXXX>"
container_name       = "tfstate"
key                  = "flexsim-prod.tfstate"   # separate file: dev and prod NEVER share a state
use_azuread_auth     = true
