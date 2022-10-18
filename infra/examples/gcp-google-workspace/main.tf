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

locals {
  base_config_path = "${var.psoxy_base_dir}/configs/"
}

module "worklytics_connector_specs" {
  # source = "../../modules/worklytics-connector-specs"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-connector-specs?ref=v0.4.6"

  enabled_connectors            = var.enabled_connectors
  google_workspace_example_user = var.google_workspace_example_user
}

# NOTE: if you don't have perms to provision a GCP project in your billing account, you can have
# someone else create one and than import it:
#  `terraform import google_project.psoxy-project your-psoxy-project-id`
# either way, we recommend the project be used exclusively to host psoxy instances corresponding to
# a single worklytics account
resource "google_project" "psoxy-project" {
  name            = "Psoxy%{if var.environment_name != ""} - ${var.environment_name}%{endif}"
  project_id      = var.gcp_project_id
  billing_account = var.gcp_billing_account_id
  folder_id       = var.gcp_folder_id # if project is at top-level of your GCP organization, rather than in a folder, comment this line out
  # org_id          = var.gcp_org_id # if project is in a GCP folder, this value is implicit and this line should be commented out
}

module "psoxy-gcp" {
  source = "../../modules/gcp"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp?ref=v0.4.6"

  project_id        = google_project.psoxy-project.project_id
  invoker_sa_emails = var.worklytics_sa_emails
  psoxy_base_dir    = var.psoxy_base_dir
  bucket_location   = var.gcp_region

  depends_on = [
    google_project.psoxy-project
  ]
}

module "google-workspace-connection" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  # source = "../../modules/google-workspace-dwd-connection"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/google-workspace-dwd-connection?ref=v0.4.6"

  project_id                   = google_project.psoxy-project.project_id
  connector_service_account_id = "psoxy-${substr(each.key, 0, 24)}"
  display_name                 = "Psoxy Connector - ${each.value.display_name}${var.connector_display_name_suffix}"
  apis_consumed                = each.value.apis_consumed
  oauth_scopes_needed          = each.value.oauth_scopes_needed
  todo_step                    = 1

  depends_on = [
    module.psoxy-gcp
  ]
}

module "google-workspace-connection-auth" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/gcp-sa-auth-key"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-sa-auth-key?ref=v0.4.6"

  service_account_id = module.google-workspace-connection[each.key].service_account_id
}

module "google-workspace-key-secrets" {

  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/gcp-secrets"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-secrets?ref=v0.4.4"

  secret_project = google_project.psoxy-project.project_id
  secrets = {
    "PSOXY_${replace(upper(each.key), "-", "_")}_SERVICE_ACCOUNT_KEY" : {
      value       = module.google-workspace-connection-auth[each.key].key_value
      description = "Auth key for ${each.key} service account"
    }
  }
}

module "psoxy-google-workspace-connector" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  # source = "../../modules/gcp-psoxy-rest"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-psoxy-rest?ref=v0.4.6"

  project_id                            = google_project.psoxy-project.project_id
  source_kind                           = each.value.source_kind
  instance_id                           = "psoxy-${each.key}"
  service_account_email                 = module.google-workspace-connection[each.key].service_account_email
  artifacts_bucket_name                 = module.psoxy-gcp.artifacts_bucket_name
  deployment_bundle_object_name         = module.psoxy-gcp.deployment_bundle_object_name
  path_to_config                        = "${local.base_config_path}${each.value.source_kind}.yaml"
  path_to_repo_root                     = var.psoxy_base_dir
  salt_secret_id                        = module.psoxy-gcp.salt_secret_id
  salt_secret_version_number            = module.psoxy-gcp.salt_secret_version_number
  example_api_calls                     = each.value.example_api_calls
  example_api_calls_user_to_impersonate = each.value.example_api_calls_user_to_impersonate
  todo_step                             = module.google-workspace-connection[each.key].next_todo_step

  secret_bindings = {
    # as SERVICE_ACCOUNT_KEY rotated by Terraform, reasonable to bind as env variable
    SERVICE_ACCOUNT_KEY = {
      secret_id      = module.google-workspace-key-secrets[each.key].secret_ids["PSOXY_${replace(upper(each.key), "-", "_")}_SERVICE_ACCOUNT_KEY"]
      version_number = module.google-workspace-key-secrets[each.key].secret_version_numbers["PSOXY_${replace(upper(each.key), "-", "_")}_SERVICE_ACCOUNT_KEY"]
    }
  }
}

module "worklytics-psoxy-connection" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  # source = "../../modules/worklytics-psoxy-connection"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection?ref=v0.4.6"

  psoxy_instance_id  = each.key
  psoxy_endpoint_url = module.psoxy-google-workspace-connector[each.key].cloud_function_url
  display_name       = "${title(each.key)}${var.connector_display_name_suffix} via Psoxy"
  todo_step          = module.psoxy-google-workspace-connector[each.key].next_todo_step
}

# BEGIN LONG ACCESS AUTH CONNECTORS

resource "google_service_account" "long_auth_connector_sa" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  project      = google_project.psoxy-project.project_id
  account_id   = "psoxy-${substr(each.key, 0, 24)}"
  display_name = "${title(each.key)}{var.connector_display_name_suffix} via Psoxy"
}

# creates the secret, without versions.
module "connector-long-auth-block" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  # source = "../../modules/gcp-oauth-long-access-strategy"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-oauth-long-access-strategy?ref=v0.4.6"

  project_id              = google_project.psoxy-project.project_id
  function_name           = "psoxy-${each.key}"
  token_adder_user_emails = []
}

module "long-auth-token-secret-fill-instructions" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  source = "../../modules/gcp-secret-fill-md"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-secret-fill-md?ref=v0.4.6"

  project_id = google_project.psoxy-project.project_id
  secret_id  = module.connector-long-auth-block[each.key].access_token_secret_id
}

module "source_token_external_todo" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors_todos

  source = "../../modules/source-token-external-todo"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/source-token-external-todo?ref=v0.4.6"

  source_id                         = each.key
  connector_specific_external_steps = each.value.external_token_todo
  todo_step                         = 1

  additional_steps = [
    module.long-auth-token-secret-fill-instructions[each.key].todo_markdown
  ]
}

module "connector-long-auth-create-function" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  # source = "../../modules/gcp-psoxy-rest"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-psoxy-rest?ref=v0.4.6"

  project_id                    = google_project.psoxy-project.project_id
  source_kind                   = each.value.source_kind
  instance_id                   = "psoxy-${each.key}"
  service_account_email         = google_service_account.long_auth_connector_sa[each.key].email
  artifacts_bucket_name         = module.psoxy-gcp.artifacts_bucket_name
  deployment_bundle_object_name = module.psoxy-gcp.deployment_bundle_object_name
  path_to_config                = "${local.base_config_path}${each.value.source_kind}.yaml"
  path_to_repo_root             = var.psoxy_base_dir
  salt_secret_id                = module.psoxy-gcp.salt_secret_id
  salt_secret_version_number    = module.psoxy-gcp.salt_secret_version_number
  todo_step                     = module.source_token_external_todo[each.key].next_todo_step

  # NOTE: ACCESS_TOKEN, ENCRYPTION_KEY not passed via secret_bindings (which would get bound as
  # env vars at function start-up) because
  #   - to be bound as env vars, secrets must already exist or function fails to start (w/o any
  #     error visible to Terraform other than timeout); ACCESS_TOKEN may need to be created manually
  #     so may not be defined at time of provisioning, and ENCRYPTION_KEY is optional
  #   - both ACCESS_TOKEN, ENCRYPTION_KEY may be subject to rotation outside of terraform; no easy
  #     way for users to force function restart, and env vars won't reload value of a secret until
  #     function restarts. Better to let app-code load these values from Secret Manager, cache with
  #     a TTL, and periodically refresh or refresh on auth errors.
}

module "worklytics-psoxy-connection-long-auth" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  # source = "../../modules/worklytics-psoxy-connection"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection?ref=v0.4.6"

  psoxy_instance_id  = each.key
  psoxy_endpoint_url = module.connector-long-auth-create-function[each.key].cloud_function_url
  display_name       = "${each.value.display_name} via Psoxy${var.connector_display_name_suffix}"
  todo_step          = module.connector-long-auth-create-function[each.key].next_todo_step
}
# END LONG ACCESS AUTH CONNECTORS

# BEGIN BULK CONNECTORS
module "psoxy-gcp-bulk" {
  for_each = merge(module.worklytics_connector_specs.enabled_bulk_connectors,
  var.custom_bulk_connectors)

  # source = "../../modules/gcp-psoxy-bulk"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-psoxy-bulk?ref=v0.4.6"

  project_id                    = google_project.psoxy-project.project_id
  worklytics_sa_emails          = var.worklytics_sa_emails
  region                        = var.gcp_region
  source_kind                   = each.value.source_kind
  salt_secret_id                = module.psoxy-gcp.salt_secret_id
  artifacts_bucket_name         = module.psoxy-gcp.artifacts_bucket_name
  deployment_bundle_object_name = module.psoxy-gcp.deployment_bundle_object_name
  salt_secret_version_number    = module.psoxy-gcp.salt_secret_version_number
  psoxy_base_dir                = var.psoxy_base_dir
  bucket_write_role_id          = module.psoxy-gcp.bucket_write_role_id

  environment_variables = {
    SOURCE = each.value.source_kind
    RULES  = yamlencode(each.value.rules)
  }

  depends_on = [
    google_project.psoxy-project,
  ]
}
