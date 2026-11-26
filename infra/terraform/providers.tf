# =====================================================================
# providers.tf   (folder: infra\terraform)
# Settings for the Azure plug-in: WHICH subscription to build in.
#
# How does Terraform log in? It BORROWS your "az login" (the Azure CLI).
# No password or key is written in any file.
# =====================================================================
provider "azurerm" {
  # "features {}" is required by the Azure plug-in, even when empty.
  # (It holds optional behaviour switches; we use the defaults.)
  features {}

  # The subscription (the "wallet") where everything is created.
  # It comes from the variables file (environments\dev.tfvars).
  # SAFETY: written in the file on purpose. Terraform builds ONLY in this
  # subscription, even if "az login" is pointing at a different one.
  subscription_id = var.subscription_id
}
