

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
