# Enable Vertex AI and grant invoke access to the given service accounts.
# `service_accounts` is keyed by stable connector ids (known at plan); emails may be
# computed and must not be used as for_each keys. No-op when the map is empty.

locals {
  services = length(var.service_accounts) > 0 ? toset(["aiplatform.googleapis.com"]) : toset([])
}

resource "google_project_service" "aiplatform" {
  for_each = local.services

  project                    = var.project_id
  service                    = each.value
  disable_dependent_services = false
  disable_on_destroy         = false
}

resource "google_project_iam_member" "vertex_user" {
  for_each = var.service_accounts

  project = var.project_id
  role    = "roles/aiplatform.user"
  member  = "serviceAccount:${each.value}"

  depends_on = [google_project_service.aiplatform]
}
