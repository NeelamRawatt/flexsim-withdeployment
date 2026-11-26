# =====================================================================
# registry.tf   (folder: infra\terraform)
# ACR = Azure Container Registry = our PRIVATE "Docker Hub" in Azure.
# Flow: build image (laptop / GitHub Actions) -> docker push to ACR -> AKS pulls from ACR.
#
# COST: Basic SKU is about $5/month (~Rs 15/day) WHILE IT EXISTS.
#       -> run "terraform destroy" when you stop practising.
# =====================================================================

resource "azurerm_container_registry" "main" {
  # Becomes the address <name>.azurecr.io
  # Must be UNIQUE IN THE WHOLE WORLD, 5-50 characters, only lowercase letters and numbers
  # (no dashes). Comes from the answers file because it differs per environment.
  name = var.acr_name

  resource_group_name = azurerm_resource_group.main.name   # our folder
  location            = azurerm_resource_group.main.location

  # SKU = price level: Basic (cheapest, enough for practice), Standard, Premium.
  sku = var.acr_sku

  # SECURITY: no shared "admin" username/password for the registry.
  # People log in with their own Azure login (az acr login) and
  # AKS gets the RBAC role "AcrPull" (download only). Nothing to leak.
  admin_enabled = false

  tags = local.common_tags
}
