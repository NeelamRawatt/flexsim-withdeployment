# =====================================================================
# eventhubs.tf   (folder: infra\terraform)
# EVENT HUBS = Azure's managed messaging service that understands KAFKA.
#   namespace = the "post office" (= Kafka cluster)
#   event hub = one "letter box"  (= Kafka topic)
# Our spring-kafka code stays the same; only settings change
# (address <name>.servicebus.windows.net:9093 + SASL_SSL login).
# COST: Standard, 1 throughput unit = about Rs 60/day while it exists.
# =====================================================================

# ---------------------------------------------------------------------
# 1) The post office (namespace).
#    Standard = Kafka door (port 9093) is ON automatically. (Basic has NO Kafka.)
#    capacity = 1 throughput unit = "1 counter open" (~1 MB/s in, 2 MB/s out).
#    minimum_tls_version = only modern encryption allowed.
#    Not in a subnet: protected by login (connection string in Key Vault) + TLS.
# ---------------------------------------------------------------------
resource "azurerm_eventhub_namespace" "main" {
  name                = var.eventhub_namespace_name   # globally unique -> <name>.servicebus.windows.net
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  sku                 = "Standard"
  capacity            = 1
  minimum_tls_version = "1.2"
  tags                = local.common_tags
}

# ---------------------------------------------------------------------
# 2) The TOPICS (event hubs) - the SAME names and partition counts as the
#    laptop Kafka (infra\docker\docker-compose.kafka.yml).
#    map { "name" = partitions }. Standard allows max 10 event hubs; we have 9.
#    NOTE: in Standard the partition count can NOT be changed later.
# ---------------------------------------------------------------------
locals {
  kafka_topics = {
    "simulation.trigger.requested"     = 4   # 4 = up to 4 simulations at the same time
    "simulation.status"                = 3
    "simulation.kpi"                   = 3
    "experiment.deleted"               = 3
    "file.validated"                   = 3
    "simulation.trigger.requested.DLT" = 4   # DLT = same partitions as its main topic
    "simulation.status.DLT"            = 3
    "simulation.kpi.DLT"               = 3
    "experiment.deleted.DLT"           = 3
  }
}

# for_each over the map: each.key = topic name, each.value = partition count.
resource "azurerm_eventhub" "topic" {
  for_each          = local.kafka_topics
  name              = each.key
  namespace_id      = azurerm_eventhub_namespace.main.id
  partition_count   = each.value
  message_retention = 1   # keep messages 1 day (services read them within seconds)
}

# ---------------------------------------------------------------------
# 3) Access key for OUR APP: send + read only (least privilege).
#    manage = false -> cannot create/delete topics or change settings.
#    Azure generates the secret key + connection string automatically.
# ---------------------------------------------------------------------
resource "azurerm_eventhub_namespace_authorization_rule" "app" {
  name                = "flexsim-app-send-listen"
  namespace_name      = azurerm_eventhub_namespace.main.name
  resource_group_name = azurerm_resource_group.main.name
  listen              = true
  send                = true
  manage              = false
}

# ---------------------------------------------------------------------
# 4) Save the app's connection string in Key Vault (never typed, not in Git).
#    primary_ = the first of Azure's 2 keys (2 keys -> rotate one without downtime).
# ---------------------------------------------------------------------
resource "azurerm_key_vault_secret" "eventhubs_connection_string" {
  name         = "eventhubs-connection-string"
  value        = azurerm_eventhub_namespace_authorization_rule.app.primary_connection_string
  key_vault_id = azurerm_key_vault.main.id

  depends_on = [azurerm_role_assignment.kv_secrets_officer_me]
}

# ---------------------------------------------------------------------
# 5) The Kafka LOGIN LINE (JAAS config) that Spring Kafka needs, built here and
#    saved in Key Vault. "$ConnectionString" is FIXED text Event Hubs expects
#    (not a variable). \" = a quote inside the text. ${...} = insert the value.
#    Pods get it as SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG (no code change).
# ---------------------------------------------------------------------
resource "azurerm_key_vault_secret" "kafka_sasl_jaas_config" {
  name         = "kafka-sasl-jaas-config"
  value        = "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$ConnectionString\" password=\"${azurerm_eventhub_namespace_authorization_rule.app.primary_connection_string}\";"
  key_vault_id = azurerm_key_vault.main.id
  depends_on   = [azurerm_role_assignment.kv_secrets_officer_me]
}
