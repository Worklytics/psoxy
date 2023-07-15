output "enabled_api_connectors" {
  description = "List of enabled api connectors"
  value       = local.enabled_api_connectors
}

output "todos" {
  description = "List of TODOS for enabled api connectors"
  value       = values(module.google_workspace_connection)[*].todo
}

output "next_todo_step" {
  value = try(max(values(module.google_workspace_connection)[*].next_todo_step...), var.todo_step)
}

