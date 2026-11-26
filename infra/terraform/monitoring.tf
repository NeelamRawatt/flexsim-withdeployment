# =====================================================================
# monitoring.tf   (folder: infra\terraform)
# MONITORING + ALERTS
#   Logs    = what happened (the "nurse's notes")
#   Metrics = numbers over time: CPU, memory... (the "heart-rate monitor")
#   Alerts  = rule + who to tell (the "alarm" + the phone number)
# 1) Log Analytics workspace = one central, searchable logbook (query language: KQL)
# 2) Container Insights (in aks.tf: oms_agent) sends every pod's logs + CPU/memory here
# 3) Action group = WHO gets told (email)
# 4) Alert rules: node CPU high, node memory high, pods restarting in namespace flexsim
# COST: per GB of logs; tiny for dev. daily_quota_gb caps it so a noisy bug can't make a big bill.
# =====================================================================

# 1) The central logbook.
resource "azurerm_log_analytics_workspace" "main" {
  name                = "log-${local.name_suffix}"   # log-flexsim-dev
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  sku                 = "PerGB2018"   # pay per GB of logs stored (the normal plan)
  retention_in_days   = 30            # keep logs 30 days (the minimum)
  daily_quota_gb      = 0.5           # stop collecting after 0.5 GB per day (cost safety)
  tags                = local.common_tags
}

# 3) WHO gets told when an alert fires.
resource "azurerm_monitor_action_group" "email" {
  name                = "ag-${local.name_suffix}-email"   # ag-flexsim-dev-email
  resource_group_name = azurerm_resource_group.main.name
  short_name          = "flexsimdev"   # max 12 characters, shown in the email/SMS
  tags                = local.common_tags

  email_receiver {
    name                    = "owner"
    email_address           = var.alert_email
    use_common_alert_schema = true   # the same, easy-to-read email layout for every alert
  }
}

# 4a) ALERT: the AKS server's CPU is above 85% on average for 15 minutes.
resource "azurerm_monitor_metric_alert" "node_cpu" {
  name                = "alert-${local.name_suffix}-node-cpu-high"
  resource_group_name = azurerm_resource_group.main.name
  scopes              = [azurerm_kubernetes_cluster.main.id]   # watch our AKS cluster
  description         = "AKS node CPU above 85% for 15 minutes (server too small or overloaded)"
  severity            = 2          # 0 = critical ... 4 = verbose; 2 = warning
  frequency           = "PT5M"     # check every 5 minutes  (PT5M = ISO time format: 5 minutes)
  window_size         = "PT15M"    # look at the last 15 minutes
  tags                = local.common_tags

  criteria {
    metric_namespace = "Microsoft.ContainerService/managedClusters"
    metric_name      = "node_cpu_usage_percentage"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 85
  }

  action {
    action_group_id = azurerm_monitor_action_group.email.id
  }
}

# 4b) ALERT: the AKS server's memory is above 85% for 15 minutes (risk of OOMKilled pods).
resource "azurerm_monitor_metric_alert" "node_memory" {
  name                = "alert-${local.name_suffix}-node-memory-high"
  resource_group_name = azurerm_resource_group.main.name
  scopes              = [azurerm_kubernetes_cluster.main.id]
  description         = "AKS node memory (working set) above 85% for 15 minutes (risk of OOMKilled pods)"
  severity            = 2
  frequency           = "PT5M"
  window_size         = "PT15M"
  tags                = local.common_tags

  criteria {
    metric_namespace = "Microsoft.ContainerService/managedClusters"
    metric_name      = "node_memory_working_set_percentage"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 85
  }

  action {
    action_group_id = azurerm_monitor_action_group.email.id
  }
}

# 4c) ALERT: a pod in namespace "flexsim" restarted more than 2 times in 15 minutes (the app is crashing).
#     A LOG alert: it runs a KQL query on the logbook every 5 minutes.
resource "azurerm_monitor_scheduled_query_rules_alert_v2" "pod_restarts" {
  name                 = "alert-${local.name_suffix}-pod-restarts"
  resource_group_name  = azurerm_resource_group.main.name
  location             = azurerm_resource_group.main.location
  scopes               = [azurerm_log_analytics_workspace.main.id]   # search in our logbook
  description          = "A pod in namespace flexsim restarted more than 2 times in 15 minutes"
  severity             = 1           # 1 = error (more serious than the CPU warning)
  evaluation_frequency = "PT5M"
  window_duration      = "PT15M"
  tags                 = local.common_tags

  criteria {
    # KQL: for each pod in flexsim, how much did its restart counter go up in the window?
    query                   = <<-KQL
      KubePodInventory
      | where Namespace == "flexsim"
      | summarize Restarts = max(PodRestartCount) - min(PodRestartCount) by Name
      | where Restarts > 2
    KQL
    time_aggregation_method = "Count"        # count the rows the query returns (= crashing pods)
    operator                = "GreaterThan"
    threshold               = 0              # fire if at least 1 pod is crashing
    failing_periods {
      minimum_failing_periods_to_trigger_alert = 1
      number_of_evaluation_periods             = 1
    }
  }

  action {
    action_groups = [azurerm_monitor_action_group.email.id]
  }
}
