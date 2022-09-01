output "key_secret_name" {
  value = google_secret_manager_secret.service-account-key.name
}

output "key_secret_id" {
  value = google_secret_manager_secret.service-account-key.secret_id
}

output "key_secret_version_name" {
  value = google_secret_manager_secret_version.service-account-key-version.name
}

output "key_secret_version_number" {
  value = trimprefix(google_secret_manager_secret_version.service-account-key-version.name, "${google_secret_manager_secret.service-account-key.name}/versions/")
}


output "key_value" {
  value = google_service_account_key.key.private_key
}


