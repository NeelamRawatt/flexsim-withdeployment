# =====================================================================
# network.tf   (folder: infra\terraform)
# The PRIVATE NETWORK for FlexSim in Azure (the "gated society").
# Step 1: only the VNet itself. The subnets ("blocks") come in the next step.
#
# Terraform reads ALL .tf files in this folder together, so this file can use
# things from main.tf (the resource group, the tags) directly.
# Cost: a VNet is FREE.
# =====================================================================

resource "azurerm_virtual_network" "main" {
  # Name pattern: vnet-flexsim-dev
  name = "vnet-${local.name_suffix}"

  # Put it INSIDE our folder, in the same city.
  # These lines REFER to the resource group, so Terraform knows:
  # "create the resource group FIRST, then the VNet".
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location

  # The range of private "house numbers" this network may use.
  # [ ] = a LIST (one network can have more than one range; we use one).
  address_space = [var.vnet_address_space]

  tags = local.common_tags
}

# ---------------------------------------------------------------------
# SUBNET 1: snet-aks = the "block" where Kubernetes (AKS) and its pods live.
# Needs many addresses (one per pod), so it gets a bigger range (/22 = ~1,000).
# Nickname "aks" (not "main"), because there will be 4 subnets of the same kind.
# No location (same city as the VNet) and no tags (Azure does not allow tags on subnets).
# ---------------------------------------------------------------------
resource "azurerm_subnet" "aks" {
  name                 = "snet-aks"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name   # which VNet (society) it belongs to
  address_prefixes     = [var.subnet_aks_prefix]              # its range; must be inside the VNet range
}

# ---------------------------------------------------------------------
# SUBNET 2: snet-postgres = a RESERVED ("delegated") block, only for PostgreSQL.
# Azure PostgreSQL Flexible Server in PRIVATE mode must live in its own
# delegated subnet. Result: the database has NO public internet address;
# only things inside our VNet can reach it.
# ---------------------------------------------------------------------
resource "azurerm_subnet" "postgres" {
  name                 = "snet-postgres"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = [var.subnet_postgres_prefix]

  # The "reserved for" sign.
  delegation {
    name = "postgres-delegation"                       # our label for this reservation
    service_delegation {
      # Fixed texts from Azure's documentation (copy as they are):
      name    = "Microsoft.DBforPostgreSQL/flexibleServers"               # reserved for: PostgreSQL Flexible Server
      actions = ["Microsoft.Network/virtualNetworks/subnets/join/action"] # PostgreSQL may "join" (use) this subnet
    }
  }
}

# ---------------------------------------------------------------------
# SUBNET 3: snet-gateway = RESERVED block for Application Gateway for Containers
# (the "main gate": HTTPS traffic from the client's browser comes in here and is
# sent to the right service in AKS). Azure requires at least a /24 for it.
# ---------------------------------------------------------------------
resource "azurerm_subnet" "gateway" {
  name                 = "snet-gateway"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = [var.subnet_gateway_prefix]

  delegation {
    name = "gateway-delegation"
    service_delegation {
      # Fixed texts from Azure's documentation:
      name    = "Microsoft.ServiceNetworking/trafficControllers"           # Application Gateway for Containers
      actions = ["Microsoft.Network/virtualNetworks/subnets/join/action"]
    }
  }
}

# ---------------------------------------------------------------------
# SUBNET 4: snet-worker = NORMAL block for the Windows VM (flexsim-worker + FlexSim).
# No delegation: a VM does not need a reserved block.
# ---------------------------------------------------------------------
resource "azurerm_subnet" "worker" {
  name                 = "snet-worker"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = [var.subnet_worker_prefix]
}
