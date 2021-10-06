output "service_account_id" {
  value = google_service_account.connector-sa.id
}

output "service_account_email" {
  value = google_service_account.connector-sa.email
}
