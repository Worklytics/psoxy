# Enable Vertex AI and grant invoke access to the given service accounts.
# No-op when `service_account_emails` is empty.

locals {
  services = length(var.service_account_emails) > 0 ? toset(["aiplatform.googleapis.com"]) : toset([])
}

resource "google_project_service" "aiplatform" {
  for_each = local.services

  project                    = var.project_id
  service                    = each.value
  disable_dependent_services = false
  disable_on_destroy         = false
}

resource "google_project_iam_member" "vertex_user" {
  for_each = var.service_account_emails

  project = var.project_id
  role    = "roles/aiplatform.user"
  member  = "serviceAccount:${each.value}"

  depends_on = [google_project_service.aiplatform]
}
