
output "enabled_rest_connectors" {
  description = "List of enabled connectors"
  value       = local.enabled_rest_connectors
}


output "enabled_bulk_connectors" {
  description = "List of enabled bulk connectors"
  value       = local.enabled_bulk_connectors
}

output "todos" {
  value = values(module.source_token_external_todo)[*].todo
}

locals {
  next_todo_step = [for v in values(module.source_token_external_todo) : tonumber(v.next_todo_step)]
}

output "next_todo_step" {
  value = max(local.next_todo_step...) + 1
}

output "next_todo_step_alt" {
  value = max(values(module.source_token_external_todo)[*].next_todo_step...) + 1
}
