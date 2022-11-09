resource "google_secret_manager_secret" "secret" {
  project   = var.project_id
  secret_id = var.secret_name

  replication {
    automatic = true
  }
}

resource "google_secret_manager_secret_version" "secret-version" {
  secret = google_secret_manager_secret.secret.id

  secret_data = "TODO: fill me with a real value!"

  lifecycle {
    ignore_changes = [
      secret_data
    ]
  }
}

# need to be able to add versions in case refresh_token rotates
resource "google_secret_manager_secret_iam_member" "grant_secretVersionAdd-on-secret" {
  member    = "serviceAccount:${var.service_account_email}"
  role      = "roles/secretmanager.secretVersionAdder"
  project   = var.project_id
  secret_id = google_secret_manager_secret.secret.secret_id
}