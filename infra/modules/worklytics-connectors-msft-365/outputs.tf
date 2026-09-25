output "enabled_api_connectors" {
  description = "List of enabled Microsoft 365 connectors"
  value       = local.enabled_api_connectors
}

output "todos" {
  description = "List of TODOS for enabled REST connectors"
  value       = values(local.msft_365_todos)
}

output "todo_files" {
  description = "TODO markdown files (filename => content) for ./generate-todos.sh. The local_file copies are deprecated and will be removed in 0.8."
  value       = local.todo_files
}


locals {
  next_todo_steps = tolist([for k, v in module.msft_365_grants : tonumber(v.next_todo_step)])
}


output "next_todo_step" {
  # TODO: fix this. tf complain is:
  # │     │ while calling max(numbers...)
  # │     │ local.next_todo_steps is empty list of dynamic
  #  │     │ var.todo_step is 1

  value = try(max(concat([var.todo_step], local.next_todo_steps)), var.todo_step + 1)
}

output "api_clients" {
  description = "Map of API client identifiers. Useful for configuration of clients, terraform migration."
  value = {
    for id, connection in module.msft_connection :
    id => {
      oauth_client_id = connection.connector.client_id
      entra_object_id = connection.connector.object_id # used for terraform imports
    }
  }
}
