resource "google_secret_manager_secret" "secret" {
  project   = var.project_id
  secret_id = "${var.path_prefix}${var.secret_name}"

  replication {
    automatic = true
  }
}

# need to be able to read secret and secret version content
resource "google_secret_manager_secret_iam_member" "grant_sa_accessor_on_secret" {
  member    = "serviceAccount:${var.service_account_email}"
  role      = "roles/secretmanager.secretAccessor"
  project   = var.project_id
  secret_id = google_secret_manager_secret.secret.secret_id
}

# need to be able to add versions in case refresh_token rotates
resource "google_secret_manager_secret_iam_member" "grant_secretVersionAdd-on-secret" {
  member    = "serviceAccount:${var.service_account_email}"
  role      = "roles/secretmanager.secretVersionAdder"
  project   = var.project_id
  secret_id = google_secret_manager_secret.secret.secret_id
}

# need to read secrets metadata, used for listing and get secret versions
resource "google_secret_manager_secret_iam_member" "grant_secretViewer-on-secret" {
  member    = "serviceAccount:${var.service_account_email}"
  role      = "roles/secretmanager.viewer"
  project   = var.project_id
  secret_id = google_secret_manager_secret.secret.secret_id
}