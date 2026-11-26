# =====================================================================
# gateway.tf   (folder: infra\terraform)
# The MAIN GATE: Application Gateway for Containers (AGC).
# The only public entry point. It receives browser requests and routes
# /api/... to the right service in AKS (rules come from Kubernetes
# Gateway API files, pushed by the ALB Controller inside AKS).
# Terraform calls it "application load balancer" (ALB).
# COST: about Rs 50-100/day while it exists -> destroy after practice.
# =====================================================================

# 1) The gateway itself ("the receptionist").
resource "azurerm_application_load_balancer" "main" {
  name                = "alb-${local.name_suffix}"   # alb-flexsim-dev
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  tags                = local.common_tags
}

# 2) Put it inside its RESERVED subnet snet-gateway (delegated to
#    Microsoft.ServiceNetworking/trafficControllers), so it can reach the private pods.
resource "azurerm_application_load_balancer_subnet_association" "main" {
  name                         = "assoc-snet-gateway"
  application_load_balancer_id = azurerm_application_load_balancer.main.id
  subnet_id                    = azurerm_subnet.gateway.id
  tags                         = local.common_tags
}

# 3) The public "front door" (frontend). Azure gives it a public web address
#    automatically (like xxxxxxxx.fzXX.alb.azure.com). The Kubernetes Gateway
#    file refers to it by this name.
resource "azurerm_application_load_balancer_frontend" "main" {
  name                         = "fe-flexsim"
  application_load_balancer_id = azurerm_application_load_balancer.main.id
  tags                         = local.common_tags
}

# =====================================================================
# The ALB CONTROLLER's ID card and permissions.
# ALB = Application Load Balancer = Terraform's name for Application Gateway for Containers.
# The ALB Controller = a small helper program INSIDE AKS. It reads our routing rules
# (Kubernetes Gateway + HTTPRoute files) and tells the Azure gateway about them.
# Same pattern as our pods + Key Vault: ID card + small permissions + trust link.
# =====================================================================

# 4) The helper's Azure ID card.
resource "azurerm_user_assigned_identity" "alb" {
  name                = "id-${local.name_suffix}-alb"   # id-flexsim-dev-alb
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  tags                = local.common_tags
}

# 5) Permission: change the routing settings of OUR gateway only.
resource "azurerm_role_assignment" "alb_config_manager" {
  scope                = azurerm_application_load_balancer.main.id
  role_definition_name = "AppGw for Containers Configuration Manager"
  principal_id         = azurerm_user_assigned_identity.alb.principal_id
}

# 6) Permission: connect the gateway to its block (snet-gateway only).
resource "azurerm_role_assignment" "alb_network" {
  scope                = azurerm_subnet.gateway.id
  role_definition_name = "Network Contributor"
  principal_id         = azurerm_user_assigned_identity.alb.principal_id
}

# 7) Permission: LOOK at (read only) AKS's automatic folder (MC_...) with the cluster's network parts.
resource "azurerm_role_assignment" "alb_reader_node_rg" {
  scope                = azurerm_kubernetes_cluster.main.node_resource_group_id
  role_definition_name = "Reader"
  principal_id         = azurerm_user_assigned_identity.alb.principal_id
}

# 8) Trust link: Azure accepts the helper's Kubernetes ID card
#    (service account "alb-controller-sa" in namespace "azure-alb-system" = names used by Microsoft's chart).
resource "azurerm_federated_identity_credential" "alb" {
  name                = "azure-alb-identity"   # REQUIRED exact name (Microsoft: other names are not supported)
  resource_group_name = azurerm_resource_group.main.name
  parent_id           = azurerm_user_assigned_identity.alb.id
  audience            = ["api://AzureADTokenExchange"]
  issuer              = azurerm_kubernetes_cluster.main.oidc_issuer_url
  subject             = "system:serviceaccount:azure-alb-system:alb-controller-sa"
}
