output "salt_secret_name" {
  value = google_secret_manager_secret.pseudonymization-salt.name
}

output "salt_secret_version_name" {
  value = google_secret_manager_secret_version.initial_version.name
}
