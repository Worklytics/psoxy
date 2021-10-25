
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

# q: access token as secret? presume that written to cloud function file system / memory sufficient
# bc 1) presumably will rotated a lot (supposed to be short-lived), 2) presumably OK for each
# function instance to end-up; 3) presumably *some* persistence of functions so will gain sufficient
# re-use and not refresh constantly
