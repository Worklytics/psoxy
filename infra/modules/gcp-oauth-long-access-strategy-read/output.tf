output "access_token_secret_name" {
  value = data.google_secret_manager_secret.access_token.name
}
