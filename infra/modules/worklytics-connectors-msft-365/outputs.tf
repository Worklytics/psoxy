output "enabled_api_connectors" {
  description = "List of enabled Microsoft 365 connectors"
  value       = local.enabled_api_connectors
}

output "todos" {
  description = "List of TODOS for enabled REST connectors"
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

  value = try(max(concat([var.todo_step], local.next_todo_steps)), var.todo_step + 1)
}

#deprecated; don't think it's used directly anywhere
output "application_ids" {
  value = {
    for id, connection in module.msft_connection : id => connection.connector.application_id
  }
}

output "api_clients" {
  description = "Map of API client identifiers. Useful for configuration of clients, terraform migration."
  value = {
    for id, connection in module.msft_connection :
    id => {
      azuread_application_id = connection.connector.application_id
      oauth_client_id        = connection.connector.application_id # yes, it's same as application id; but duplicated for clarity
      azuread_object_id      = connection.connector.object_id  # used for terraform imports
    }
  }
}
