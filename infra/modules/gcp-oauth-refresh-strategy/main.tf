
resource "google_secret_manager_secret" "client_secret" {
  project   = var.project_id
  secret_id = "PSOXY_${var.function_name}_CLIENT_SECRET"

  replication {
    automatic = true
  }
}

resource "google_secret_manager_secret" "refresh_token" {
  project   = var.project_id
  secret_id = "PSOXY_${var.function_name}_REFRESH_TOKEN"

  replication {
    automatic = true
  }
}

# need to be able to add versions in case refresh_token rotates
resource "google_secret_manager_secret_iam_member" "grant_secretVersionAdd_on_refreshToken_to_functionSA" {
  member    = "serviceAccount:${var.service_account_email}"
  role      = "roles/secretmanager.secretVersionAdder"
  secret_id = google_secret_manager_secret.refresh_token
}


# not 'proper' terraform style to invoke modules hierarchically rather than via composition; but
# composing them is complex and exposes implementation details (eg, that refresh token strategy
# requires two secrets)
module "client_secret_grant" {
  source = "../gcp-secret-user-version-adder"

  project_id     = var.project_id
  secret_id      = google_secret_manager_secret.client_secret.secret_id
  grant_duration = var.token_adder_grant_duration
  user_emails    = var.token_adder_user_emails
}

module "refresh_token_grant" {
  source = "../gcp-secret-user-version-adder"

  project_id     = var.project_id
  secret_id      = google_secret_manager_secret.refresh_token.secret_id
  grant_duration = var.token_adder_grant_duration
  user_emails    = var.token_adder_user_emails
}
