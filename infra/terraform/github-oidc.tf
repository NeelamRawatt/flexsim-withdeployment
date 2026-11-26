# =====================================================================
# github-oidc.tf   (folder: infra\terraform)
# Lets GitHub Actions (the CD pipeline) log in to Azure WITHOUT any password
# ("OIDC login"): Azure trusts GitHub Actions runs from OUR repository,
# environment "dev" only. Same pattern as the pods and the ALB helper:
# ID card + trust link + small permissions. Cost: Rs 0.
# =====================================================================

# 1) GitHub's Azure ID card. It lives in rg-flexsim-tfstate (NEVER destroyed), so its
#    client ID stays the SAME every day and GitHub's settings never need updating.
#    Created ONCE by hand:
#      az identity create --name id-flexsim-github --resource-group rg-flexsim-tfstate --location swedencentral
#    Terraform only LOOKS IT UP ("data" = ask, create nothing).
data "azurerm_user_assigned_identity" "github" {
  name                = "id-flexsim-github"
  resource_group_name = "rg-flexsim-tfstate"
}

# 2) Trust link: only GitHub Actions jobs from OUR repo that use the GitHub environment "dev".
#    (A fork, another repo, or a job without environment "dev" cannot use this ID card.)
resource "azurerm_federated_identity_credential" "github_dev" {
  name                = "fic-github-dev"
  resource_group_name = data.azurerm_user_assigned_identity.github.resource_group_name
  parent_id           = data.azurerm_user_assigned_identity.github.id
  audience            = ["api://AzureADTokenExchange"]
  issuer              = "https://token.actions.githubusercontent.com"   # GitHub Actions issues the ID cards
  subject             = "repo:${var.github_repo}:environment:dev"
}

# 3) Permission: upload (push) images to our registry.
resource "azurerm_role_assignment" "github_acr_push" {
  scope                = azurerm_container_registry.main.id
  role_definition_name = "AcrPush"
  principal_id         = data.azurerm_user_assigned_identity.github.principal_id
}

# 4) Permission: get the cluster's "key" (like az aks get-credentials) to run helm upgrade.
resource "azurerm_role_assignment" "github_aks_user" {
  scope                = azurerm_kubernetes_cluster.main.id
  role_definition_name = "Azure Kubernetes Service Cluster User Role"
  principal_id         = data.azurerm_user_assigned_identity.github.principal_id
}

# 5) Permission: LOOK at our resource group (read IDs like the pods' client ID, the gateway ID).
resource "azurerm_role_assignment" "github_rg_reader" {
  scope                = azurerm_resource_group.main.id
  role_definition_name = "Reader"
  principal_id         = data.azurerm_user_assigned_identity.github.principal_id
}
