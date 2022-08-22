output "salt_secret_name" {
  value = google_secret_manager_secret.pseudonymization-salt.name
}

output "salt_secret_version_number" {
  value = trimprefix(google_secret_manager_secret_version.initial_version.name, "${google_secret_manager_secret.pseudonymization-salt.name}/versions/")
}

output "artifacts_bucket_name" {
  value = google_storage_bucket.artifacts.name
}

output "deployment_bundle_object_name" {
  value = google_storage_bucket_object.function.name
}
