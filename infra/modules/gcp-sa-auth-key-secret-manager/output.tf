output "key_secret" {
  value = google_secret_manager_secret.service-account-key.id
}

output "key_value" {
  value = google_service_account_key.key.private_key
}
