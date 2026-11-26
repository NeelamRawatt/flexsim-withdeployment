# =====================================================================
# main.tf   (folder: infra\terraform)
# WHAT we want in Azure. For now: only ONE resource group.
#
# RESOURCE GROUP = a "folder" in Azure that holds related things.
# It is free (an empty folder costs nothing).
# Later, AKS, PostgreSQL, Key Vault... all go INSIDE this folder.
# Deleting the folder deletes everything inside it.
# =====================================================================

# "locals" = values we calculate ONCE and reuse (like a final variable in Java).
locals {
  # One naming pattern for everything: <type>-flexsim-<environment>
  # e.g. rg-flexsim-dev. Clear names = easy to find and to clean up.
  name_suffix = "flexsim-${var.environment}"

  # TAGS = labels on every Azure resource (like Kubernetes labels).
  # Used to filter costs ("how much did flexsim dev cost?") and to know who owns what.
  # Company policies often REQUIRE certain tags.
  common_tags = {
    project     = "flexsim"
    environment = var.environment
    owner       = var.owner
    managed-by  = "terraform"   # "do not change this by hand in the portal"
  }
}

# A RESOURCE block = one thing to create.
#   "azurerm_resource_group" = the TYPE (from the Azure plug-in)
#   "main"                   = OUR short name for it inside Terraform
#                              (other files refer to it as azurerm_resource_group.main)
resource "azurerm_resource_group" "main" {
  name     = "rg-${local.name_suffix}"   # rg-flexsim-dev
  location = var.location
  tags     = local.common_tags
}
