# =====================================================================
# outputs.tf   (folder: infra\terraform)
# OUTPUTS = values Terraform prints at the end of "apply"
# (and you can read later with: terraform output).
# Like a method's return value.
# =====================================================================

output "resource_group_name" {
  description = "Name of the resource group that holds everything"
  value       = azurerm_resource_group.main.name
}

output "location" {
  description = "Azure region in use"
  value       = azurerm_resource_group.main.location
}

output "vnet_name" {
  description = "Name of the private network"
  value       = azurerm_virtual_network.main.name
}

output "acr_login_server" {
  description = "Address of the container registry, used to tag and push images"
  value       = azurerm_container_registry.main.login_server
}

output "key_vault_uri" {
  description = "Address (URL) of the Key Vault"
  value       = azurerm_key_vault.main.vault_uri
}

output "postgres_fqdn" {
  description = "Database address that services use in their JDBC URL"
  value       = azurerm_postgresql_flexible_server.main.fqdn
}

output "kafka_bootstrap_servers" {
  description = "Kafka address for the services (Event Hubs Kafka endpoint)"
  value       = "${azurerm_eventhub_namespace.main.name}.servicebus.windows.net:9093"
}

output "aks_name" {
  description = "Name of the AKS cluster (used to connect kubectl)"
  value       = azurerm_kubernetes_cluster.main.name
}
