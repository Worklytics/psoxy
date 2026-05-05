output "enabled_api_connectors" {
  description = "List of enabled Microsoft 365 connectors"
  value       = local.enabled_api_connectors
}

output "todos" {
  description = "[DEPRECATED - use todo_content output instead. TODO: remove in 0.7] List of TODOS for enabled REST connectors"
  value       = values(module.msft_365_grants)[*].todo
}


locals {
  next_todo_steps = tolist([for k, v in module.msft_365_grants : tonumber(v.next_todo_step)])
}


output "next_todo_step" {
  # TODO: fix this. tf complain is:
  # │     │ while calling max(numbers...)
  # │     │ local.next_todo_steps is empty list of dynamic
  #  │     │ var.todo_step is 1
  value       = try(max(concat([var.todo_step], local.next_todo_steps)), var.todo_step + 1)
  description = "[DEPRECATED - todo ordering now handled at root module level via todo_content stage indices. TODO: remove in 0.7]"
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

output "todo_content" {
  description = "Structured todo content aggregated from all Microsoft 365 grant sub-modules (with external token todos merged in). List of stages; each stage is a list of {name, content, file_permission} objects."
  value = flatten([for k, v in local.todo_content_by_connector : v])
}
