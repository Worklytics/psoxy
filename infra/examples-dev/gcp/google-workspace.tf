provider "google" {
  alias = "google_workspace"

  project                     = var.google_workspace_gcp_project_id
  impersonate_service_account = var.google_workspace_sa_to_impersonate
}


module "worklytics_connectors_google_workspace" {
  source = "../../modules/worklytics-connectors-google-workspace"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-connectors-google-workspace?ref=rc-v0.7.1"

  google_workspace_connector_settings = var.google_workspace_connector_settings


  providers = {
    google = google.google_workspace
  }

  environment_id                 = var.environment_name
  host_platform_id               = "GCP"
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

# Cloud Function SAs (host project) need Token Creator on each DWD SA (GWS project) to call signJwt.
resource "google_service_account_iam_member" "gws_dwd_token_creator" {
  for_each = {
    for k, v in module.worklytics_connectors_google_workspace.enabled_api_connectors :
    k => v if try(v.source_auth_strategy, "") == "gcp_iam_sign_jwt"
  }

  provider           = google.google_workspace
  service_account_id = module.worklytics_connectors_google_workspace.api_clients[each.key].service_account_id
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = "serviceAccount:${module.psoxy.api_connector_gcp_execution_service_accounts[each.key].email}"
}
