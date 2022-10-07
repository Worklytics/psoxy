output "service_account_id" {
  value = google_service_account.connector-sa.id
}

output "service_account_email" {
  value = google_service_account.connector-sa.email
}

output "next_todo_step" {
  value = var.todo_step + 1
}
