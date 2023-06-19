module "worklytics_connectors_google_workspace" {
  source = "../../modules/worklytics-connectors-google-workspace"

  environment_id                 = var.environment_name
  enabled_connectors             = var.enabled_connectors
  gcp_project_id                 = var.gcp_project_id
  google_workspace_example_user  = var.google_workspace_example_user
  google_workspace_example_admin = var.google_workspace_example_admin
}
