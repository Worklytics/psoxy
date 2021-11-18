output "access_token_secret_name" {
  value = google_secret_manager_secret.access_token.name
}
