terraform {
  required_providers {
    google = {
      version = ">= 4.0, <= 5.0"
    }
  }

  # if you leave this as local, you should backup/commit your TF state files
  backend "local" {
  }
}

module "worklytics_connector_specs" {
  source = "../../modules/worklytics-connector-specs"

  enabled_connectors = [
    "asana",
    "gdirectory",
    "gcal",
    "gmail",
    "google-meet",
    "google-chat",
  ]
  google_workspace_example_user = var.google_workspace_example_user
}

# NOTE: if you don't have perms to provision a GCP project in your billing account, you can have
# someone else create one and than import it:
#  `terraform import google_project.psoxy-project your-psoxy-project-id`
# either way, we recommend the project be used exclusively to host psoxy instances corresponding to
# a single worklytics account
resource "google_project" "psoxy-project" {
  name            = "Psoxy%{if var.environment_name != ""} - ${var.environment_name}%{endif}"
  project_id      = var.project_id
  folder_id       = var.folder_id
  billing_account = var.billing_account_id
}

module "psoxy-gcp" {
  source = "../../modules/gcp"

  project_id        = google_project.psoxy-project.project_id
  invoker_sa_emails = var.worklytics_sa_emails

  depends_on = [
    google_project.psoxy-project
  ]
}

locals {
  enabled_connectors = [
    "asana"
  ]
}

module "google-workspace-connection" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/google-workspace-dwd-connection"

  project_id                   = var.project_id
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

  source = "../../modules/gcp-sa-auth-key-secret-manager"

  secret_project     = var.project_id
  service_account_id = module.google-workspace-connection[each.key].service_account_id
  secret_id          = "PSOXY_${replace(upper(each.key), "-", "_")}_SERVICE_ACCOUNT_KEY"
}

module "psoxy-google-workspace-connector" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/gcp-psoxy-rest"

  project_id                            = var.project_id
  instance_id                           = "psoxy-${each.key}"
  service_account_email                 = module.google-workspace-connection[each.key].service_account_email
  artifacts_bucket_name                 = module.psoxy-gcp.artifacts_bucket_name
  deployment_bundle_object_name         = module.psoxy-gcp.deployment_bundle_object_name
  path_to_config                        = "../../config/${each.key}.yaml"
  salt_secret_id                        = module.psoxy-gcp.salt_secret_name
  salt_secret_version_number            = module.psoxy-gcp.salt_secret_version_number
  example_api_calls                     = each.value.example_api_calls
  example_api_calls_user_to_impersonate = each.value.example_api_calls_user_to_impersonate

  secret_bindings = {
    SERVICE_ACCOUNT_KEY = {
      secret_name    = module.google-workspace-connection-auth[each.key].key_secret_name
      version_number = module.google-workspace-connection-auth[each.key].key_secret_version_number
    }
  }


}

module "worklytics-psoxy-connection" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/worklytics-psoxy-connection"

  psoxy_endpoint_url = module.psoxy-google-workspace-connector[each.key].cloud_function_url
  display_name       = "${each.value.display_name} via Psoxy"
}

# BEGIN LONG ACCESS AUTH CONNECTORS

resource "google_service_account" "long_auth_connector_sa" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  project      = var.project_id
  account_id   = each.value.function_name
  display_name = "Psoxy Connector - ${title(each.key)}{var.connector_display_name_suffix}"
}

# creates the secret, without versions.
module "connector-long-auth-block" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  source                  = "../../modules/gcp-oauth-long-access-strategy"
  project_id              = var.project_id
  function_name           = each.value.function_name
  token_adder_user_emails = []
}

module "connector-long-auth-create-function" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  source = "../../modules/gcp-psoxy-rest"

  project_id                    = var.project_id
  instance_id                   = each.value.function_name
  service_account_email         = google_service_account.long_auth_connector_sa[each.key].email
  artifacts_bucket_name         = module.psoxy-gcp.artifacts_bucket_name
  deployment_bundle_object_name = module.psoxy-gcp.deployment_bundle_object_name
  path_to_config                = "${var.psoxy_base_dir}/config/${each.value.source_kind}.yaml"
  path_to_repo_root             = var.psoxy_base_dir
  salt_secret_id                = module.psoxy-gcp.salt_secret_name
  salt_secret_version_number    = module.psoxy-gcp.salt_secret_version_number

  secret_bindings = {
    PSOXY_SALT = {
      secret_name    = module.psoxy-gcp.salt_secret_name
      version_number = module.psoxy-gcp.salt_secret_version_number
    },
    ACCESS_TOKEN = {
      secret_name = module.connector-long-auth-block[each.key].access_token_secret_name
      # in case of long lived tokens we want latest version always
      version_number = "latest"
    }
  }

}

# END LONG ACCESS AUTH CONNECTORS

