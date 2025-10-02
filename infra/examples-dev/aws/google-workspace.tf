provider "google" {
  alias = "google_workspace"

  project                     = var.google_workspace_gcp_project_id
  impersonate_service_account = var.google_workspace_sa_to_impersonate != null ? var.google_workspace_sa_to_impersonate : var.google_workspace_terraform_sa_account_email # TODO: remove ternary in 0.6.x
}


module "worklytics_connectors_google_workspace" {
  source = "../../modules/worklytics-connectors-google-workspace"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-connectors-google-workspace?ref=v0.5.10"
  
  providers = {
    google = google.google_workspace
  }

  environment_id                 = var.environment_name
  enabled_connectors             = var.enabled_connectors
  gcp_project_id                 = var.google_workspace_gcp_project_id
  tf_gcp_principal_email         = var.google_workspace_terraform_principal_email
  google_workspace_example_user  = var.google_workspace_example_user
  google_workspace_example_admin = var.google_workspace_example_admin
  provision_gcp_sa_keys          = var.google_workspace_provision_keys
  gcp_sa_key_rotation_days       = var.google_workspace_key_rotation_days
  todos_as_local_files           = var.todos_as_local_files
}

output "google_workspace_api_clients" {
  description = "Map of API client identifiers for Google Workspace connectors. Useful for migrations."
  value       = module.worklytics_connectors_google_workspace.api_clients
}
