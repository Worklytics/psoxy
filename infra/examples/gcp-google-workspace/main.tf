terraform {
  required_providers {
    google = {
      version = "~> 4.12"
    }
  }

  # if you leave this as local, you should backup/commit your TF state files
  backend "local" {
  }
}

module "worklytics_connector_specs" {
  source = "../../modules/worklytics-connector-specs"

  enabled_connectors = [
    "gdirectory",
    "gcal",
    "gmail",
    "google-meet",
    "google-chat",
    "asana",
    "slack-discovery-api",
    "zoom",
  ]
  google_workspace_example_user = var.google_workspace_example_user
}

# NOTE: if you don't have perms to provision a GCP project in your billing account, you can have
# someone else create one and than import it:
#  `terraform import google_project.psoxy-project your-psoxy-project-id`
# either way, we recommend the project be used exclusively to host psoxy instances corresponding to
# a single worklytics account
resource "google_project" "psoxy-project" {
  name            = "Worklytics Psoxy%{if var.environment_name != ""} - ${var.environment_name}%{endif}"
  project_id      = var.gcp_project_id
  billing_account = var.gcp_billing_account_id
  folder_id       = var.gcp_folder_id # if project is at top-level of your GCP organization, rather than in a folder, comment this line out
  # org_id          = var.gcp_org_id # if project is in a GCP folder, this value is implicit and this line should be commented out
}

module "psoxy-gcp" {
  # source = "../../modules/gcp"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp?ref=v0.4.1"

  project_id        = google_project.psoxy-project.project_id
  invoker_sa_emails = var.worklytics_sa_emails
  psoxy_base_dir    = var.psoxy_base_dir

  depends_on = [
    google_project.psoxy-project
  ]
}


module "google-workspace-connection" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  # source = "../../modules/google-workspace-dwd-connection"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/google-workspace-dwd-connection?ref=v0.4.1"


  project_id                   = google_project.psoxy-project.project_id
  connector_service_account_id = "psoxy-${each.key}-dwd"
  display_name                 = "Psoxy Connector - ${each.value.display_name}${var.connector_display_name_suffix}"
  apis_consumed                = each.value.apis_consumed
  oauth_scopes_needed          = each.value.oauth_scopes_needed

  depends_on = [
    module.psoxy-gcp
  ]
}

module "google-workspace-connection-auth" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  # source = "../../modules/gcp-sa-auth-key-secret-manager"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-sa-auth-key-secret-manager?ref=v0.4.1"

  secret_project     = google_project.psoxy-project.project_id
  service_account_id = module.google-workspace-connection[each.key].service_account_id
  secret_id          = "PSOXY_${replace(upper(each.key), "-", "_")}_SERVICE_ACCOUNT_KEY"
}

module "psoxy-google-workspace-connector" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  # source = "../../modules/gcp-psoxy-rest"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-psoxy-rest?ref=v0.4.1"

  project_id                            = google_project.psoxy-project.project_id
  instance_id                           = "psoxy-${each.value.source_kind}"
  service_account_email                 = module.google-workspace-connection[each.key].service_account_email
  artifacts_bucket_name                 = module.psoxy-gcp.artifacts_bucket_name
  deployment_bundle_object_name         = module.psoxy-gcp.deployment_bundle_object_name
  path_to_config                        = "${var.psoxy_base_dir}/config/${each.value.source_kind}.yaml"
  path_to_repo_root                     = var.psoxy_base_dir
  example_api_calls                     = each.value.example_api_calls
  example_api_calls_user_to_impersonate = each.value.example_api_calls_user_to_impersonate

  salt_secret_id             = module.psoxy-gcp.salt_secret_name
  salt_secret_version_number = module.psoxy-gcp.salt_secret_version_number

  secret_bindings = {
    SERVICE_ACCOUNT_KEY = {
      secret_name    = module.google-workspace-connection-auth[each.key].key_secret_name
      version_number = module.google-workspace-connection-auth[each.key].key_secret_version_number
    }
  }

}

module "worklytics-psoxy-connection" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  # source = "../../modules/worklytics-psoxy-connection"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection?ref=v0.4.1"

  psoxy_endpoint_url = module.psoxy-google-workspace-connector[each.key].cloud_function_url
  display_name       = "${each.value.display_name} via Psoxy"
}
