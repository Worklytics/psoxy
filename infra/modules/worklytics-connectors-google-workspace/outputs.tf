output "enabled_api_connectors" {
  description = "List of enabled api connectors"
  value       = local.enabled_api_connectors
}

output "todos" {
  description = "[DEPRECATED - use todo_content output instead. TODO: remove in 0.7] List of TODOS for enabled api connectors"
  value       = local.todos
}

output "next_todo_step" {
  value       = local.next_todo_step
  description = "[DEPRECATED - todo ordering now handled at root module level via todo_content stage indices. TODO: remove in 0.7]"
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

output "todo_content" {
  description = "Structured todo content to be written to local files by root module. First stage: DWD setup todos; second stage (if not provisioning keys via TF): key creation todos."
  value = concat(
    # Stage from DWD connection submodule (contains setup todos for each connector)
    flatten([for k, v in module.google_workspace_connection : v.todo_content]),
    # If users manage keys themselves, add a stage for key creation todos
    length(local.service_accounts_user_managed_keys) > 0 ? [[
      for k in keys(local.service_accounts_user_managed_keys) : {
        name            = "Create Key for ${k}"
        content         = local.key_creation_todos[k]
        file_permission = null
      }
    ]] : []
  )
}
