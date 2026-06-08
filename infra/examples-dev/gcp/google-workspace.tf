provider "google" {
  alias = "google_workspace"

  project                     = var.google_workspace_gcp_project_id
  impersonate_service_account = var.google_workspace_sa_to_impersonate
}


module "worklytics_connectors_google_workspace" {
  source = "../../modules/worklytics-connectors-google-workspace"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-connectors-google-workspace?ref=v0.6.3"

  google_workspace_connector_settings = var.google_workspace_connector_settings


  providers = {
    google = google.google_workspace
  }

  environment_id                 = var.environment_name
  base_dir                       = var.psoxy_base_dir
  enabled_connectors             = var.enabled_connectors
  gcp_project_id                 = var.google_workspace_gcp_project_id
  tf_gcp_principal_email         = var.google_workspace_terraform_principal_email
  google_workspace_example_user  = var.google_workspace_example_user
  google_workspace_example_admin = var.google_workspace_example_admin
  provision_gcp_sa_keys          = var.google_workspace_provision_keys
  gcp_sa_key_rotation_days       = var.google_workspace_key_rotation_days
  todos_as_local_files           = var.todos_as_local_files
  todo_step                      = 1
}

output "google_workspace_api_clients" {
  description = "Map of API client identifiers for Google Workspace connectors. Useful for migrations."
  value       = module.worklytics_connectors_google_workspace.api_clients
}
