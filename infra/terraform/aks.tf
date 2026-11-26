# =====================================================================
# aks.tf   (folder: infra\terraform)
# AKS = Azure Kubernetes Service = the SAME Kubernetes as Docker Desktop,
# but on Azure servers. Azure runs the "brain" (control plane, Free tier = Rs 0);
# we pay for the servers (nodes).
# COST: 1 x Standard_B2ms node = about Rs 170/day while it runs. Takes 5-10 min to create.
# Azure also creates its own extra resource group "MC_..." for the node's disks/network.
# =====================================================================

resource "azurerm_kubernetes_cluster" "main" {
  name                = "aks-${local.name_suffix}"   # aks-flexsim-dev
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  dns_prefix          = "aks-${local.name_suffix}"   # short name used in the cluster's internal address
  sku_tier            = "Free"                       # the Kubernetes "brain" costs nothing

  # The servers (node pool): size + how many + which subnet.
  default_node_pool {
    name           = "system"                  # lowercase letters only, max 12 chars
    vm_size        = var.aks_node_size         # e.g. Standard_B2ms (2 CPU, 8 GB)
    node_count     = var.aks_node_count        # e.g. 1 for practice
    vnet_subnet_id = azurerm_subnet.aks.id     # servers (and pods) live in snet-aks

    # Same values Azure sets by default (so code and Azure match, no endless "change").
    # max_surge = during upgrades add 1 temporary extra server -> no downtime.
    upgrade_settings {
      max_surge                     = "10%"
      drain_timeout_in_minutes      = 0   # 0 = Azure default wait for pods to move off
      node_soak_duration_in_minutes = 0   # 0 = no extra wait between servers
    }
  }

  # AKS's own "ID card" (managed identity), created and deleted automatically with the cluster.
  identity {
    type = "SystemAssigned"
  }

  # "azure" = each pod gets its own private IP from snet-aks (can reach the private database).
  network_profile {
    network_plugin = "azure"
  }

  # Switch ON "ID cards for pods" (workload identity). Wired to the services in D3.
  oidc_issuer_enabled       = true
  workload_identity_enabled = true

  # Switch ON the helper that fetches secrets from Key Vault into pods.
  key_vault_secrets_provider {
    secret_rotation_enabled = true   # picks up changed secrets automatically
  }

  # Container Insights: send every pod's logs + CPU/memory to the Log Analytics workspace
  # (monitoring.tf). msi_auth = it logs in with AKS's own ID card, no key.
  oms_agent {
    log_analytics_workspace_id      = azurerm_log_analytics_workspace.main.id
    msi_auth_for_monitoring_enabled = true
  }

  tags = local.common_tags
}

# ---------------------------------------------------------------------
# PERMISSIONS (RBAC) for AKS's 2 automatic ID cards (least privilege).
# ---------------------------------------------------------------------

# The SERVERS' ID card (kubelet identity) may DOWNLOAD images from our registry. Nothing else.
# [0] = the first item of the list Azure returns.
resource "azurerm_role_assignment" "aks_acr_pull" {
  scope                = azurerm_container_registry.main.id
  role_definition_name = "AcrPull"
  principal_id         = azurerm_kubernetes_cluster.main.kubelet_identity[0].object_id
}

# The CLUSTER's ID card may plug things into its own subnet (snet-aks only, not the whole VNet).
resource "azurerm_role_assignment" "aks_network" {
  scope                = azurerm_subnet.aks.id
  role_definition_name = "Network Contributor"
  principal_id         = azurerm_kubernetes_cluster.main.identity[0].principal_id
}
