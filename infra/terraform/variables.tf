# =====================================================================
# variables.tf   (folder: infra\terraform)
# The "blanks" of our Terraform code (like Helm's values).
# Here we only DECLARE them (name, type, meaning).
# The ANSWERS are in environments\dev.tfvars (personal free account)
# and later environments\prod.tfvars (company subscription).
# =====================================================================

variable "subscription_id" {
  description = "Azure subscription ID to build in. Find it with: az account show --query id -o tsv"
  type        = string
}

variable "environment" {
  description = "Short environment name, used in resource names and tags: dev, test or prod"
  type        = string

  # validation = a check BEFORE anything is created. A typo like "prd" stops here.
  validation {
    condition     = contains(["dev", "test", "prod"], var.environment)
    error_message = "environment must be dev, test or prod."
  }
}

variable "location" {
  description = "Azure region (the data centre city), e.g. centralindia, swedencentral"
  type        = string
}

variable "owner" {
  description = "Who is responsible for these resources (shown as a tag)"
  type        = string
}

variable "vnet_address_space" {
  description = "Private address range of the VNet in CIDR form, e.g. 10.10.0.0/16"
  type        = string
}

variable "subnet_aks_prefix" {
  description = "Address range of the AKS subnet (must be inside the VNet range), e.g. 10.10.0.0/22"
  type        = string
}

variable "subnet_postgres_prefix" {
  description = "Address range of the PostgreSQL subnet (must be inside the VNet range), e.g. 10.10.8.0/24"
  type        = string
}

variable "subnet_gateway_prefix" {
  description = "Address range of the gateway subnet (must be /24 or bigger), e.g. 10.10.9.0/24"
  type        = string
}

variable "subnet_worker_prefix" {
  description = "Address range of the Windows worker VM subnet, e.g. 10.10.10.0/24"
  type        = string
}

variable "acr_name" {
  description = "Container registry name: globally unique, 5-50 chars, only lowercase letters and numbers"
  type        = string

  # regex = a text pattern: only a-z and 0-9, between 5 and 50 characters.
  validation {
    condition     = can(regex("^[a-z0-9]{5,50}$", var.acr_name))
    error_message = "acr_name must be 5-50 characters, only lowercase letters and numbers (no dashes)."
  }
}

variable "acr_sku" {
  description = "Container registry price level: Basic, Standard or Premium"
  type        = string

  validation {
    condition     = contains(["Basic", "Standard", "Premium"], var.acr_sku)
    error_message = "acr_sku must be Basic, Standard or Premium."
  }
}

variable "key_vault_name" {
  description = "Key Vault name: globally unique, 3-24 characters, letters, numbers and dashes"
  type        = string

  # Azure's name rules: start with a letter, letters/numbers/dashes, no dash at the end, 3-24 chars.
  validation {
    condition     = can(regex("^[a-zA-Z][a-zA-Z0-9-]{1,22}[a-zA-Z0-9]$", var.key_vault_name))
    error_message = "key_vault_name: 3-24 characters, start with a letter, only letters/numbers/dashes, not ending with a dash."
  }
}

variable "key_vault_purge_protection" {
  description = "true = nobody can empty the recycle bin early (use true in prod, false in dev)"
  type        = bool   # yes/no: only true or false (no quotes)
}

variable "postgres_server_name" {
  description = "PostgreSQL server name: globally unique, 3-63 chars, lowercase letters, numbers and dashes"
  type        = string
}

variable "postgres_sku" {
  description = "PostgreSQL machine size, e.g. B_Standard_B1ms (smallest)"
  type        = string
}

variable "eventhub_namespace_name" {
  description = "Event Hubs namespace name: globally unique, 6-50 chars, letters, numbers and dashes, start with a letter"
  type        = string
}

variable "aks_node_size" {
  description = "Size of each AKS server (VM), e.g. Standard_B2ms (2 CPU, 8 GB)"
  type        = string
}

variable "aks_node_count" {
  description = "How many AKS servers (nodes): 1 for practice, 2+ for production"
  type        = number   # a number, written without quotes
}

variable "github_repo" {
  description = "GitHub repository allowed to deploy, exactly as in GitHub OIDC subject: owner@ownerId/repo@repoId"
  type        = string
}
