# =====================================================================
# dev.backend.hcl   (folder: infra\terraform\environments)
# WHERE the "dev" state (receipt) is stored in Azure.
# The storage was created ONCE with az CLI in its own resource group
# (rg-flexsim-tfstate), which our normal "terraform destroy" never touches.
# Used by:  terraform init -backend-config="environments\dev.backend.hcl"
# =====================================================================

resource_group_name  = "rg-flexsim-tfstate"   # separate folder for state
storage_account_name = "stflexsimtfneelam"    # the "drive" (globally unique name)
container_name       = "tfstate"              # folder inside the drive
key                  = "flexsim-dev.tfstate"  # the state file name for DEV (prod gets its own)
use_azuread_auth     = true                   # use my Azure login + "Storage Blob Data Contributor" role, not the shared key
