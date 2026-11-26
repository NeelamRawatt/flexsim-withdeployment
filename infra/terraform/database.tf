# =====================================================================
# database.tf   (folder: infra\terraform)
# Azure Database for PostgreSQL - Flexible Server (managed PostgreSQL 16).
# PRIVATE access: lives in snet-postgres, private IP only, found by name via a private DNS zone.
# COST: about Rs 40-50/day while it exists (B1ms + 32 GB) -> destroy after practice.
# =====================================================================

# ---------------------------------------------------------------------
# 1) Make a strong random admin password (made ONCE, then kept the same - it is in the state).
#    special = false: only letters and numbers (symbols can break connection strings).
# ---------------------------------------------------------------------
resource "random_password" "postgres_admin" {
  length  = 32
  special = false
}

# ---------------------------------------------------------------------
# 2) Save that password in Key Vault (the locker). Nobody types it; it is not in Git.
#    depends_on: wait until MY "Key Vault Secrets Officer" role exists,
#    otherwise Key Vault would refuse (403). Nothing here points to that role,
#    so we tell Terraform the order ourselves.
# ---------------------------------------------------------------------
resource "azurerm_key_vault_secret" "postgres_admin_password" {
  name         = "postgres-admin-password"
  value        = random_password.postgres_admin.result
  key_vault_id = azurerm_key_vault.main.id

  depends_on = [azurerm_role_assignment.kv_secrets_officer_me]
}

# ---------------------------------------------------------------------
# 3) The PRIVATE PHONE BOOK (private DNS zone): turns the database's NAME into its private IP.
#    Azure rule: the name must end with ".postgres.database.azure.com".
#    No location: DNS zones are "global".
# ---------------------------------------------------------------------
resource "azurerm_private_dns_zone" "postgres" {
  name                = "flexsim-${var.environment}.private.postgres.database.azure.com"
  resource_group_name = azurerm_resource_group.main.name
  tags                = local.common_tags
}

# ---------------------------------------------------------------------
# 4) CONNECT the phone book to our VNet, so everything inside the VNet can use it.
#    registration_enabled = false: do NOT add every machine automatically;
#    Azure adds only the database's record itself.
# ---------------------------------------------------------------------
resource "azurerm_private_dns_zone_virtual_network_link" "postgres" {
  name                  = "link-postgres-to-vnet"
  resource_group_name   = azurerm_resource_group.main.name
  private_dns_zone_name = azurerm_private_dns_zone.postgres.name
  virtual_network_id    = azurerm_virtual_network.main.id
  registration_enabled  = false
  tags                  = local.common_tags
}

# ---------------------------------------------------------------------
# 5) The PostgreSQL SERVER itself (private, PostgreSQL 16, same as the laptop).
#    Takes 5-10 minutes to create. About Rs 40-50/day while it exists.
# ---------------------------------------------------------------------
resource "azurerm_postgresql_flexible_server" "main" {
  # Globally unique -> becomes <name>.postgres.database.azure.com
  name                = var.postgres_server_name
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  version             = "16"

  # PRIVATE: inside the reserved subnet, uses our private phone book, no internet address.
  delegated_subnet_id           = azurerm_subnet.postgres.id
  private_dns_zone_id           = azurerm_private_dns_zone.postgres.id
  public_network_access_enabled = false

  # The admin (master user). Used only by us for setup; services use their own users.
  administrator_login    = "flexsimadmin"
  administrator_password = random_password.postgres_admin.result

  # Size: B_Standard_B1ms = smallest (1 CPU, 2 GB). 32768 MB = 32 GB disk (minimum).
  sku_name              = var.postgres_sku
  storage_mb            = 32768
  backup_retention_days = 7   # automatic backups, restore to any minute of the last 7 days

  tags = local.common_tags

  # Azure needs the phone book CONNECTED to the VNet before the server is created.
  depends_on = [azurerm_private_dns_zone_virtual_network_link.postgres]

  # We did not choose an availability zone, so Azure picked one (e.g. zone 1).
  # Without this, Terraform tries to "reset" the zone on every apply and Azure refuses.
  # ignore_changes = "if these differ in Azure, leave them alone".
  lifecycle {
    ignore_changes = [zone, high_availability[0].standby_availability_zone]
  }
}

# ---------------------------------------------------------------------
# 6) The 6 DATABASES inside the server (one per service, like the laptop).
#    Same names in every environment -> a local list, not a variable.
# ---------------------------------------------------------------------
locals {
  postgres_databases = ["auth_db", "experiment_db", "file_db", "simulation_db", "kpi_db", "ai_db"]
}

# for_each = a LOOP: this block is repeated once for each name in the list.
# each.key = the current name (auth_db, then experiment_db, ...).
# The 6 database USERS (auth_user, ...) are created later with SQL.
resource "azurerm_postgresql_flexible_server_database" "service" {
  for_each  = toset(local.postgres_databases)
  name      = each.key
  server_id = azurerm_postgresql_flexible_server.main.id
  charset   = "UTF8"        # stores text in every language
  collation = "en_US.utf8"  # text sorting rules
}
