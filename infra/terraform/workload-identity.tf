# =====================================================================
# workload-identity.tf   (folder: infra\terraform)
# The Azure "ID card" for OUR PODS (workload identity), so they can READ
# their passwords from Key Vault without any password of their own.
#   1) the ID card (user-assigned managed identity)
#   2) its only permission: read secrets in our Key Vault
#   3) the trust links to the 5 Kubernetes service accounts (federated credentials)
# Cost: Rs 0.
# =====================================================================

# 1) The ID card. "User-assigned" = we create it as its own resource (not tied to AKS),
#    so our pods can use it.
resource "azurerm_user_assigned_identity" "app" {
  name                = "id-${local.name_suffix}-app"   # id-flexsim-dev-app
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  tags                = local.common_tags
}

# 2) Its ONLY permission: READ secrets (cannot add, change or delete). Least privilege.
resource "azurerm_role_assignment" "app_kv_secrets_user" {
  scope                = azurerm_key_vault.main.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_user_assigned_identity.app.principal_id
}

# 3) The TRUST LINKS (federated credentials), ONE PER SERVICE:
#    Azure accepts the Kubernetes service account "<service>" (namespace "flexsim")
#    from OUR cluster as this Azure ID card. No password involved.
#    One service account per service, because one Kubernetes object can belong to
#    only ONE Helm release. All 5 use the same Azure ID card (same read-only access).
resource "azurerm_federated_identity_credential" "app" {
  for_each            = toset(["auth-service", "experiment-service", "file-service", "simulation-service", "kpi-service"])
  name                = "fic-${each.key}"
  resource_group_name = azurerm_resource_group.main.name
  parent_id           = azurerm_user_assigned_identity.app.id
  audience            = ["api://AzureADTokenExchange"]                 # fixed text from Azure docs
  issuer              = azurerm_kubernetes_cluster.main.oidc_issuer_url  # our cluster's "ID-card office"
  subject             = "system:serviceaccount:flexsim:${each.key}"     # ONLY these 5 service accounts are trusted
}
