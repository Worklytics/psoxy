
output "enabled_rest_connectors" {
  description = "List of enabled connectors"
  value       = local.enabled_rest_connectors
}


output "enabled_bulk_connectors" {
  description = "List of enabled bulk connectors"
  value       = local.enabled_bulk_connectors
}

output "todos" {
  value = values(module.source_token_external_todo[*]).todo
}

output "next_todo_step" {
  value = module.source_token_external_todo.next_todo_step
}
