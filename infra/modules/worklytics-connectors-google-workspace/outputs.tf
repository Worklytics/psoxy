output "enabled_rest_connectors" {
  description = "List of enabled Google Workspace connectors"
  value       = local.enabled_rest_connectors
}

output "enabled_rest_connectors_todos" {
  description = "List of TODOS for enabled REST connectors"
  value       = values(module.google_workspace_connection[*]).todo
}

output "next_todo_step" {
  value = max(values(module.google_workspace_connection[*]).next_todo_step) + 1
}
