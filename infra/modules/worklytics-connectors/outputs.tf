
output "enabled_api_connectors" {
  description = "List of enabled api connectors"
  value       = local.enabled_api_connectors
}


output "enabled_bulk_connectors" {
  description = "List of enabled bulk connectors"
  value       = local.enabled_bulk_connectors
}

output "todos" {
  value = values(module.source_token_external_todo)[*].todo
}

output "next_todo_step" {
  value = max(values(module.source_token_external_todo)[*].next_todo_step...)
}
