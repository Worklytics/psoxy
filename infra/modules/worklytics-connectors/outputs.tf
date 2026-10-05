
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
  value = values(module.source_token_external_todo)[*].todo
}

output "todo_files" {
  description = "TODO markdown files (filename => content) for ./generate-todos.sh. The local_file copies are deprecated and will be removed in 0.8."
  value = merge(concat(
    [{}],
    [for todo in values(module.source_token_external_todo) : todo.todo_files],
  )...)
}

output "next_todo_step" {
  value = try(max(values(module.source_token_external_todo)[*].next_todo_step...), var.todo_step)
}
