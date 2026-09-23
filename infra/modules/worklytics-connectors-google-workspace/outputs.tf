output "enabled_api_connectors" {
  description = "List of enabled api connectors"
  value       = local.enabled_api_connectors
}

output "todos" {
  description = "List of TODOS for enabled api connectors"
  value       = local.todos
}

output "todo_files" {
  description = "TODO markdown files (filename => content) for ./generate-todos.sh. The local_file copies are deprecated and will be removed in 0.8."
  value       = local.todo_files
}

output "next_todo_step" {
  value = local.next_todo_step
}

output "api_clients" {
  description = "Map of API clients identifiers for Google Workspace connectors. Useful for migrations."
  value = { for k, v in module.google_workspace_connection :
    k => {
      service_account_id = v.service_account_id
      oauth_client_id    = v.service_account_numeric_id
    }
  }
}
