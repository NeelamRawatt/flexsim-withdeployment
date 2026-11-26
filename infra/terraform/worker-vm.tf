# =====================================================================
# worker-vm.tf   (folder: infra\terraform)
# The WINDOWS VM for flexsim-worker + FlexSim (FlexSim only runs on Windows).
# Lives in snet-worker, PRIVATE address only (safe remote access = step 2).
# COST: about Rs 250-300/day while it exists (Windows licence included) -> destroy after practice.
# =====================================================================

# 1) Admin password for Windows: made by Terraform, never typed, not in Git.
#    Windows needs 3 of 4 kinds (upper, lower, number, symbol) -> we require all 4.
resource "random_password" "worker_vm_admin" {
  length           = 24
  min_upper        = 2
  min_lower        = 2
  min_numeric      = 2
  min_special      = 2
  override_special = "!@#%*-_"   # only simple symbols (easy to paste into Remote Desktop)
}

# 2) Save it in Key Vault. Read it with:
#    az keyvault secret show --vault-name kv-flexsim-dev-neelam --name worker-vm-admin-password --query value -o tsv
resource "azurerm_key_vault_secret" "worker_vm_admin_password" {
  name         = "worker-vm-admin-password"
  value        = random_password.worker_vm_admin.result
  key_vault_id = azurerm_key_vault.main.id
  depends_on   = [azurerm_role_assignment.kv_secrets_officer_me]
}

# 3) The NETWORK CARD: plugs the VM into snet-worker with a PRIVATE address.
#    (No public IP here. Step 2 adds safe access for you only.)
resource "azurerm_network_interface" "worker" {
  name                = "nic-${local.name_suffix}-worker"   # nic-flexsim-dev-worker
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  tags                = local.common_tags

  ip_configuration {
    name                          = "internal"
    subnet_id                     = azurerm_subnet.worker.id   # the block we reserved for the worker
    private_ip_address_allocation = "Dynamic"                  # Azure picks a free private address
    # Public address ONLY when enable_worker_rdp = true (dev). Production: no public IP (use Azure Bastion/VPN).
    public_ip_address_id          = var.enable_worker_rdp ? azurerm_public_ip.worker[0].id : null
  }
}

# 4) The WINDOWS COMPUTER itself.
resource "azurerm_windows_virtual_machine" "worker" {
  name                = "vm-${local.name_suffix}-worker"   # vm-flexsim-dev-worker (Azure name)
  computer_name       = "flexsim-worker"                   # name INSIDE Windows (max 15 characters)
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  size                = var.worker_vm_size                 # Standard_D2s_v6 = 2 CPU, 8 GB

  # The admin user to log in with Remote Desktop (password from step 1, stored in Key Vault).
  admin_username = "flexsimadmin"
  admin_password = random_password.worker_vm_admin.result

  # Plug in the network card from step 3.
  network_interface_ids = [azurerm_network_interface.worker.id]

  # v6 sizes use the newer, faster NVMe disk connection; Azure refuses the VM without this.
  disk_controller_type = "NVMe"

  # The C: drive: 128 GB Standard SSD (Windows + FlexSim + run folders).
  os_disk {
    caching              = "ReadWrite"
    storage_account_type = "StandardSSD_LRS"
    disk_size_gb         = 128
  }

  # Which Windows to install: Windows Server 2022 Datacenter, Azure Edition (Gen2, supports NVMe).
  source_image_reference {
    publisher = "MicrosoftWindowsServer"
    offer     = "WindowsServer"
    sku       = "2022-datacenter-azure-edition"
    version   = "latest"
  }

  # Keep a screenshot/log of the boot screen (helps if the VM does not start). Uses Azure-managed storage.
  boot_diagnostics {}

  tags = local.common_tags
}

# =====================================================================
# SAFE REMOTE DESKTOP (RDP, port 3389) - ONLY from my own internet address.
# Dev only. In production: no public IP; use Azure Bastion or a VPN instead.
# =====================================================================

# 5) A public address for the VM (so my laptop can find it from the internet).
resource "azurerm_public_ip" "worker" {
  count               = var.enable_worker_rdp ? 1 : 0   # 1 = create it (dev), 0 = do not (prod)
  name                = "pip-${local.name_suffix}-worker"   # pip-flexsim-dev-worker
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  allocation_method   = "Static"     # the address does not change while the VM exists
  sku                 = "Standard"   # Standard = closed by default; only what the NSG allows gets in
  tags                = local.common_tags
}

# 6) The firewall (network security group) with ONE allow rule: RDP from my address only.
#    Everything else from the internet is blocked by Azure's default rules.
resource "azurerm_network_security_group" "worker" {
  name                = "nsg-${local.name_suffix}-worker"   # nsg-flexsim-dev-worker
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  tags                = local.common_tags

  # The RDP rule exists ONLY when enable_worker_rdp = true ("dynamic" = make this block 0 or 1 times).
  dynamic "security_rule" {
    for_each = var.enable_worker_rdp ? [1] : []
    content {
      name                       = "Allow-RDP-from-admin-ip"
      priority                   = 100              # lower number = checked first
      direction                  = "Inbound"        # traffic coming IN to the VM
      access                     = "Allow"
      protocol                   = "Tcp"
      source_address_prefix      = var.admin_ip     # ONLY my internet address (/32 = exactly one)
      source_port_range          = "*"
      destination_address_prefix = "*"
      destination_port_range     = "3389"           # Remote Desktop
    }
  }
}

# 7) Put the firewall in front of the VM's network card.
resource "azurerm_network_interface_security_group_association" "worker" {
  network_interface_id      = azurerm_network_interface.worker.id
  network_security_group_id = azurerm_network_security_group.worker.id
}

# The public IP became "count" (0 or 1). This tells Terraform the existing one is now
# number [0], so it is NOT deleted and re-created.
moved {
  from = azurerm_public_ip.worker
  to   = azurerm_public_ip.worker[0]
}
