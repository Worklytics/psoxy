
output "enabled_api_connectors" {
  description = "List of enabled api connectors"
  value       = local.enabled_api_connectors
}


output "enabled_bulk_connectors" {
  description = "List of enabled bulk connectors"
  value       = local.enabled_bulk_connectors
}

output "available_connector_ids" {
  description = "List of available connector ids"
  value       = module.worklytics_connector_specs.available_connector_ids
}

output "todos" {
  value       = values(module.source_token_external_todo)[*].todo
  description = "[DEPRECATED - use todo_content output instead. TODO: remove in 0.7]"
}

output "next_todo_step" {
  value       = try(max(values(module.source_token_external_todo)[*].next_todo_step...), var.todo_step)
  description = "[DEPRECATED - todo ordering now handled at root module level via todo_content stage indices. TODO: remove in 0.7]"
}

output "todo_content" {
  description = "Structured todo content aggregated from all source token external todo sub-modules. List of stages; each stage is a list of {name, content, file_permission} objects."
  value = flatten([for m in values(module.source_token_external_todo) : m.todo_content])
}
