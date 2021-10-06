output "key_secret_name" {
  value = google_secret_manager_secret.service-account-key.name
}

output "key_secret_version_name" {
  value = google_secret_manager_secret_version.service-account-key-version.name
}

output "key_value" {
  value = google_service_account_key.key.private_key
}
