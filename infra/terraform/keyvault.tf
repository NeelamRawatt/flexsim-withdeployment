# =====================================================================
# keyvault.tf   (folder: infra\terraform)
# KEY VAULT = Azure's encrypted "bank locker" for passwords, keys and certificates.
# Who may open it is decided by RBAC roles (not by a shared password).
# Cost: almost Rs 0 (pay per use, tiny).
# =====================================================================

# ---------------------------------------------------------------------
# "data" = ASK Azure something (creates NOTHING).
# Question: "Who is running Terraform right now, and in which tenant?"
# Answers we use:
#   data.azurerm_client_config.current.tenant_id = my "branch" (tenant)
#   data.azurerm_client_config.current.object_id = MY personal ID in Azure
# ---------------------------------------------------------------------
data "azurerm_client_config" "current" {}

# ---------------------------------------------------------------------
# The password locker itself.
# ---------------------------------------------------------------------
resource "azurerm_key_vault" "main" {
  # Globally unique, 3-24 characters: letters, numbers and dashes.
  name = var.key_vault_name

  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location

  # The locker belongs to MY tenant ("branch").
  tenant_id = data.azurerm_client_config.current.tenant_id

  # "standard" = normal locker. ("premium" = special key hardware, not needed.)
  sku_name = "standard"

  # Who may open it = decided by RBAC roles (the modern, recommended way).
  rbac_authorization_enabled = true   # (new name of the old "enable_rbac_authorization")

  # Recycle bin: after deletion, kept hidden for 7 days (the minimum) in case it was a mistake.
  soft_delete_retention_days = 7

  # Purge protection = "nobody may empty the recycle bin early".
  # dev: false (so the daily destroy -> apply works). prod: true (safety).
  purge_protection_enabled = var.key_vault_purge_protection

  tags = local.common_tags
}

# ---------------------------------------------------------------------
# Put MY name on the locker's list (RBAC role assignment).
#   WHO   = me (my ID from the "who am I?" question above)
#   ROLE  = Key Vault Secrets Officer = may add, read, change, delete secrets
#   WHERE = only this Key Vault
# (Being Owner of the subscription does NOT let you read secrets; this role does.)
# The apps will later get the smaller role "Key Vault Secrets User" (read only).
# ---------------------------------------------------------------------
resource "azurerm_role_assignment" "kv_secrets_officer_me" {
  scope                = azurerm_key_vault.main.id
  role_definition_name = "Key Vault Secrets Officer"
  principal_id         = data.azurerm_client_config.current.object_id
}

# ---------------------------------------------------------------------
# Passwords for the FIRST LOGIN USERS that auth-service creates at its first start
# ("developer" = you, "flexsim-worker" = the Windows worker's account).
# Read yours with:
#   az keyvault secret show --vault-name kv-flexsim-dev-neelam --name auth-developer-password --query value -o tsv
# ---------------------------------------------------------------------
resource "random_password" "auth_bootstrap" {
  for_each = toset(["developer", "worker"])
  length   = 24
  special  = false
}

resource "azurerm_key_vault_secret" "auth_bootstrap_password" {
  for_each     = toset(["developer", "worker"])
  name         = "auth-${each.key}-password"     # auth-developer-password, auth-worker-password
  value        = random_password.auth_bootstrap[each.key].result
  key_vault_id = azurerm_key_vault.main.id
  depends_on   = [azurerm_role_assignment.kv_secrets_officer_me]
}
