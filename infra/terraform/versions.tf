# =====================================================================
# versions.tf   (folder: infra\terraform)
# Which Terraform version and which "providers" (plug-ins) this code needs.
#
# PROVIDER = a plug-in that teaches Terraform how to talk to one platform.
#   azurerm = the official plug-in for Azure ("rm" = Resource Manager, Azure's API).
# "terraform init" downloads the providers listed here into a hidden .terraform folder.
# =====================================================================
terraform {
  # The Terraform program must be at least 1.9 (you have 1.17.2).
  required_version = ">= 1.9"

  required_providers {
    azurerm = {
      source = "hashicorp/azurerm"   # where to download it from (the Terraform Registry)
      # "~> 4.0" = any 4.x version (4.1, 4.20, ...) but NOT 5.0.
      # A new MAJOR version (5.0) can change how things work, so we only take it on purpose.
      version = "~> 4.0"
    }

    # random = a small plug-in that makes random values (we use it for strong passwords).
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"   # any 3.x from 3.6 up, not 4.0
    }
  }
}
