output "enabled_rest_connectors" {
  description = "List of enabled Microsoft 365 connectors"
  value       = local.enabled_rest_connectors
}

output "todos" {
  description = "List of TODOS for enabled REST connectors"
  value       = values(module.msft_365_grants[*]).todo
}

output "next_todo_step" {
  value = max(values(module.msft_365_grants[*]).next_todo_step) + 1
}

