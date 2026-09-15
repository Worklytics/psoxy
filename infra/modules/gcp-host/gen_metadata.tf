# Vertex IAM for genMetadata (cloud-only; no project budget resources)

check "gen_metadata_backend_gcp_only" {
  assert {
    condition = alltrue([
      for k, backend in local.connector_gen_metadata_backend :
      backend == null || backend == "vertex"
    ])
    error_message = "gcp-host genMetadata backend must be \"vertex\" (Bedrock is AWS-only)."
  }
}

locals {
  gen_metadata_vertex_sa_emails = merge(
    {
      for k, backend in local.connector_gen_metadata_backend : k => google_service_account.api_connectors[k].email
      if backend == "vertex" && contains(keys(var.api_connectors), k)
    },
    {
      for k, backend in local.connector_gen_metadata_backend : k => module.bulk_connector[k].instance_sa_email
      if backend == "vertex" && contains(keys(var.bulk_connectors), k)
    },
  )
}

resource "google_project_service" "aiplatform" {
  count = local.gen_metadata_uses_vertex ? 1 : 0

  project                    = var.gcp_project_id
  service                    = "aiplatform.googleapis.com"
  disable_dependent_services = false
  disable_on_destroy         = false
}

resource "google_project_iam_member" "gen_metadata_vertex_user" {
  for_each = local.gen_metadata_vertex_sa_emails

  project = var.gcp_project_id
  role    = "roles/aiplatform.user"
  member  = "serviceAccount:${each.value}"

  depends_on = [google_project_service.aiplatform]
}
