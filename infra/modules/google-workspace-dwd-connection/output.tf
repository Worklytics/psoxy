output "service_account_id" {
  value = google_service_account.connector-sa.id
}

output "service_account_email" {
  value = google_service_account.connector-sa.email
}

output "service_account_numeric_id" {
  value = google_service_account.connector-sa.unique_id
}

output "next_todo_step" {
  value = var.todo_step + 1
}

output "todo" {
  value = local.todo_content
}
