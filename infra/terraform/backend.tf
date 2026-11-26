# =====================================================================
# backend.tf   (folder: infra\terraform)
# BACKEND = where Terraform keeps its "receipt" (state).
# "azurerm" = in an Azure Storage account (shared, safe, locked during apply),
# instead of a file on the laptop.
#
# The details are EMPTY here on purpose: the backend cannot use variables,
# so each environment gives its own details file ("partial configuration"):
#   terraform init -backend-config="environments\dev.backend.hcl"
# =====================================================================
terraform {
  backend "azurerm" {}
}
