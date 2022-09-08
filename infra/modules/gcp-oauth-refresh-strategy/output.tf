output "client_id_secret_id" {
  value = google_secret_manager_secret.client_id.secret_id
}

output "client_secret_secret_id" {
  value = google_secret_manager_secret.client_secret.secret_id
}

output "refresh_token_secret_id" {
  value = google_secret_manager_secret.refresh_token.secret_id
}