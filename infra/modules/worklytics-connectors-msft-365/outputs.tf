output "enabled_rest_connectors" {
  description = "List of enabled Microsoft 365 connectors"
  value       = module.worklytics_connector_specs.enabled_msft_365_connectors
}

output "todos" {
  description = "List of TODOS for enabled REST connectors"
  value       = values(module.msft_365_grants)[*].todo
}

output "next_todo_step" {
  value = max(values(module.msft_365_grants)[*].next_todo_step...) + 1
}

output "application_ids" {
  value = {
    for id, connector in module.msft_connection: id => connector.application_id
  }
}
