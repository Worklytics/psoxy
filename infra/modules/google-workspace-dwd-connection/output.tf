output "instance_id" {
  value = var.instance_id
}

output "service_account_id" {
  value = google_service_account.connector_sa.id
}

output "service_account_email" {
  value = google_service_account.connector_sa.email
}

output "service_account_numeric_id" {
  value = google_service_account.connector_sa.unique_id
}

output "next_todo_step" {
  value       = var.todo_step + 1
  description = "[DEPRECATED - todo ordering now handled at root module level via todo_content stage indices. TODO: remove in 0.7]"
}

output "todo" {
  value       = local.todo_content
  description = "[DEPRECATED - use todo_content output instead. TODO: remove in 0.7]"
}

output "todo_content" {
  description = "Structured todo content to be written to local files by root module. List of stages; each stage is a list of {name, content, file_permission} objects."
  value = [[
    {
      name            = "set up ${local.instance_id}"
      content         = local.todo_content
      file_permission = null
    }
  ]]
}
