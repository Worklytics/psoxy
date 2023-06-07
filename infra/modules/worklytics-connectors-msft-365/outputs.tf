output "enabled_api_connectors" {
  description = "List of enabled Microsoft 365 connectors"
  value       = local.enabled_api_connectors
}

output "todos" {
  description = "List of TODOS for enabled REST connectors"
  value       = values(module.msft_365_grants)[*].todo
}


locals {
  next_todo_steps = tolist([ for k, v in module.msft_365_grants: tonumber(v.next_todo_step)])
}


output "next_todo_step" {
  # TODO: fix this. tf complain is:
  # │     │ while calling max(numbers...)
  # │     │ local.next_todo_steps is empty list of dynamic
  #  │     │ var.todo_step is 1

  value = try(max(concat([ var.todo_step], local.next_todo_steps )), var.todo_step + 1)
}

output "application_ids" {
  value = {
    for id, connector in module.msft_connection: id => connector.application_id
  }
}
