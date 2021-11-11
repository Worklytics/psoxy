locals {
  secret_id = "PSOXY_ACCESS_TOKEN_${var.function_name}"
}

# secret to hold a long-lived access token
resource "google_secret_manager_secret" "access_token" {
  project   = var.project_id
  secret_id = local.secret_id

  replication {
    automatic = true
  }
}

module "access_token_grant" {
  source = "../gcp-secret-user-version-adder"

  project_id     = var.project_id
  secret_id      = google_secret_manager_secret.access_token.secret_id
  grant_duration = var.token_adder_grant_duration
  user_emails    = var.token_adder_user_emails
}
