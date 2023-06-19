provider "google" {
  alias = "google_workspace"

  project                     = var.google_workspace_gcp_project_id
  impersonate_service_account = var.google_workspace_terraform_sa_account_email
}


module "worklytics_connectors_google_workspace" {
  source = "../../modules/worklytics-connectors-google-workspace"

  providers = {
    google = google.google_workspace
  }

  environment_id                 = var.environment_name
  enabled_connectors             = var.enabled_connectors
  gcp_project_id                 = var.google_workspace_gcp_project_id
  google_workspace_example_user  = var.google_workspace_example_user
  google_workspace_example_admin = var.google_workspace_example_admin
}
