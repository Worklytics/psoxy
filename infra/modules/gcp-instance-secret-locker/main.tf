resource "google_secret_manager_secret" "secret" {
  project   = var.project_id
  secret_id = "${var.path_prefix}${var.secret_name}"

  replication {
    automatic = true
  }
}

# need to update the label of the secret
resource "google_secret_manager_secret_iam_member" "grant_sa_updater_on_secret" {
  member    = "serviceAccount:${var.service_account_email}"
  role      = var.updater_role_id
  project   = var.project_id
  secret_id = google_secret_manager_secret.secret.secret_id
}