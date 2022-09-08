resource "google_secret_manager_secret" "client_id" {
  project   = var.project_id
  secret_id = "${upper(replace(var.function_name, "-", "_"))}_CLIENT_ID"

  replication {
    automatic = true
  }
}

resource "google_secret_manager_secret" "client_secret" {
  project   = var.project_id
  secret_id = "${upper(replace(var.function_name, "-", "_"))}_CLIENT_SECRET"

  replication {
    automatic = true
  }
}

resource "google_secret_manager_secret" "refresh_token" {
  project   = var.project_id
  secret_id = "${upper(replace(var.function_name, "-", "_"))}_REFRESH_TOKEN"

  replication {
    automatic = true
  }
}

resource "google_secret_manager_secret_version" "client-id-secret-version" {
  secret = google_secret_manager_secret.client_id.id

  secret_data = "TODO: fill me with a real clientId!"

  lifecycle {
    ignore_changes = [
      secret_data
    ]
  }
}

resource "google_secret_manager_secret_version" "client-secret-secret-version" {
  secret = google_secret_manager_secret.client_secret.id

  secret_data = "TODO: fill me with a real clientSecret!"

  lifecycle {
    ignore_changes = [
      secret_data
    ]
  }
}

resource "google_secret_manager_secret_version" "refresh-token-secret-version-basic" {
  secret = google_secret_manager_secret.refresh_token.id

  secret_data = "TODO: fill me with a real refresh token!"

  lifecycle {
    ignore_changes = [
      secret_data
    ]
  }
}

# need to be able to add versions in case refresh_token rotates
resource "google_secret_manager_secret_iam_member" "grant_secretVersionAdd_on_refreshToken_to_functionSA" {
  member    = "serviceAccount:${var.service_account_email}"
  role      = "roles/secretmanager.secretVersionAdder"
  project = var.project_id
  secret_id = google_secret_manager_secret.refresh_token.secret_id
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