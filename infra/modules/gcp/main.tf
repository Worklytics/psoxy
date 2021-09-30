

resource "google_project" "psoxy-project" {
  name            = "Psoxy - ${var.environment_name}"
  project_id      = var.project_id
  folder_id       = var.folder_id
  billing_account = var.billing_account_id
}


# activate required GCP service APIs
resource "google_project_service" "gcp-infra-api" {
  for_each = toset([
    "cloudfunctions.googleapis.com",
    #"iam.googleapis.com", # manage IAM via terraform
    "secretmanager.googleapis.com",
    # "cloudbuild.googleapis.com", # some modes of Cloud Functions seem to need this, so TBD
    # "dlp.googleapis.com", # Data Loss Prevention API; if in v2 we support using this to filter with AI
  ])

  service                    = each.key
  project                    = google_project.psoxy-project.project_id
  disable_dependent_services = false

  depends_on = [
    google_project.psoxy-project # don't try to setup until project exists
  ]
}

# pseudo secret
resource "google_secret_manager_secret" "pseudonymization-salt" {
  project   = google_project.psoxy-project.project_id
  secret_id = "PSOXY_SALT"

  replication {
    automatic = true
  }

  depends_on = [
    google_project_service.gcp-infra-api
  ]
}

# not really a 'password', but 'random_string' isn't "sensitive" by terraform, so
# is output to console
resource "random_password" "random" {
  length           = 20
  special          = true
}

# initial random salt to use; if you DON'T want this in your Terraform state, create a new version
# via some other means (eg, directly in GCP console). this should be done BEFORE your psoxy
# instance pseudonymizes anything; if salt is changed later, pseudonymization output will differ so
# previously pseudonymized data will be inconsistent with data pseudonymized after the change.
#
# To be clear, possession of salt alone doesn't let someone reverse pseudonyms.
resource "google_secret_manager_secret_version" "initial_version" {
  secret      = google_secret_manager_secret.pseudonymization-salt.id
  secret_data = sensitive(random_password.random.result)
}

