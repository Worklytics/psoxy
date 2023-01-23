terraform {
  required_providers {
    google = {
      version = "~> 4.12"
    }
  }
}

locals {
  base_config_path = "${var.psoxy_base_dir}/configs/"
  host_platform_id = "GCP"
}

module "worklytics_connector_specs" {
  source = "../../modules/worklytics-connector-specs"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-connector-specs?ref=v0.4.10"

  enabled_connectors             = var.enabled_connectors
  google_workspace_example_user  = var.google_workspace_example_user
  google_workspace_example_admin = coalesce(var.google_workspace_example_admin, var.google_workspace_example_user)
  msft_tenant_id = var.msft_tenant_id
}

module "psoxy-gcp" {
  source = "../../modules/gcp"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp?ref=v0.4.10"

  project_id        = var.gcp_project_id
  invoker_sa_emails = var.worklytics_sa_emails
  psoxy_base_dir    = var.psoxy_base_dir
  bucket_location   = var.gcp_region
  force_bundle      = var.force_bundle
}

module "google-workspace-connection" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/google-workspace-dwd-connection"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/google-workspace-dwd-connection?ref=v0.4.10"

  project_id                   = var.gcp_project_id
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
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-sa-auth-key?ref=v0.4.10"

  service_account_id = module.google-workspace-connection[each.key].service_account_id
}

module "google-workspace-key-secrets" {

  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/gcp-secrets"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-secrets?ref=v0.4.10"

  secret_project = var.gcp_project_id
  secrets = {
    "PSOXY_${replace(upper(each.key), "-", "_")}_SERVICE_ACCOUNT_KEY" : {
      value       = module.google-workspace-connection-auth[each.key].key_value
      description = "Auth key for ${each.key} service account"
    }
  }
}

moved {
  from = module.google-workspace-connection-auth["gdirectory"].google_secret_manager_secret.service-account-key
  to   = module.google-workspace-key-secrets["gdirectory"].google_secret_manager_secret.secret["PSOXY_GDIRECTORY_SERVICE_ACCOUNT_KEY"]
}
moved {
  from = module.google-workspace-connection-auth["gcal"].google_secret_manager_secret.service-account-key
  to   = module.google-workspace-key-secrets["gcal"].google_secret_manager_secret.secret["PSOXY_GCAL_SERVICE_ACCOUNT_KEY"]
}
moved {
  from = module.google-workspace-connection-auth["gmail"].google_secret_manager_secret.service-account-key
  to   = module.google-workspace-key-secrets["gmail"].google_secret_manager_secret.secret["PSOXY_GMAIL_SERVICE_ACCOUNT_KEY"]
}
moved {
  from = module.google-workspace-connection-auth["gdrive"].google_secret_manager_secret.service-account-key
  to   = module.google-workspace-key-secrets["gdrive"].google_secret_manager_secret.secret["PSOXY_GDRIVE_SERVICE_ACCOUNT_KEY"]
}
moved {
  from = module.google-workspace-connection-auth["google-chat"].google_secret_manager_secret.service-account-key
  to   = module.google-workspace-key-secrets["google-chat"].google_secret_manager_secret.secret["PSOXY_GOOGLE_CHAT_SERVICE_ACCOUNT_KEY"]
}
moved {
  from = module.google-workspace-connection-auth["google-meet"].google_secret_manager_secret.service-account-key
  to   = module.google-workspace-key-secrets["google-meet"].google_secret_manager_secret.secret["PSOXY_GOOGLE_MEET_SERVICE_ACCOUNT_KEY"]
}

module "psoxy-google-workspace-connector" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/gcp-psoxy-rest"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-psoxy-rest?ref=v0.4.10"

  project_id                            = var.gcp_project_id
  source_kind                           = each.value.source_kind
  instance_id                           = "psoxy-${each.key}"
  service_account_email                 = module.google-workspace-connection[each.key].service_account_email
  artifacts_bucket_name                 = module.psoxy-gcp.artifacts_bucket_name
  deployment_bundle_object_name         = module.psoxy-gcp.deployment_bundle_object_name
  path_to_config                        = "${local.base_config_path}${each.value.source_kind}.yaml"
  path_to_repo_root                     = var.psoxy_base_dir
  example_api_calls                     = each.value.example_api_calls
  example_api_calls_user_to_impersonate = each.value.example_api_calls_user_to_impersonate
  todo_step                             = module.google-workspace-connection[each.key].next_todo_step

  environment_variables = merge(
    var.general_environment_variables,
    try(each.value.environment_variables, {}),
    {
      IS_DEVELOPMENT_MODE = contains(var.non_production_connectors, each.key)
    }
  )

  secret_bindings = merge({
    # as SERVICE_ACCOUNT_KEY rotated by Terraform, reasonable to bind as env variable
    SERVICE_ACCOUNT_KEY = {
      secret_id      = module.google-workspace-key-secrets[each.key].secret_ids_within_project["PSOXY_${replace(upper(each.key), "-", "_")}_SERVICE_ACCOUNT_KEY"]
      version_number = module.google-workspace-key-secrets[each.key].secret_version_numbers["PSOXY_${replace(upper(each.key), "-", "_")}_SERVICE_ACCOUNT_KEY"]
    }
  }, module.psoxy-gcp.secrets)
}

module "worklytics-psoxy-connection" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/worklytics-psoxy-connection"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection?ref=v0.4.10"

  psoxy_host_platform_id = local.host_platform_id
  psoxy_instance_id      = each.key
  connector_id           = try(each.value.worklytics_connector_id, "")
  psoxy_endpoint_url     = module.psoxy-google-workspace-connector[each.key].cloud_function_url
  display_name           = "${title(each.key)}${var.connector_display_name_suffix} via Psoxy"
  todo_step              = module.psoxy-google-workspace-connector[each.key].next_todo_step
}

# BEGIN LONG ACCESS AUTH CONNECTORS
locals {
  long_access_parameters = { for entry in module.worklytics_connector_specs.enabled_oauth_secrets_to_create : "${entry.connector_name}.${entry.secret_name}" => entry }
  long_access_parameters_by_connector = { for k, spec in module.worklytics_connector_specs.enabled_oauth_long_access_connectors :
    k => [for secret in spec.secured_variables : "${k}.${secret.name}"]
  }
}

resource "google_service_account" "long_auth_connector_sa" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  project      = var.gcp_project_id
  account_id   = "psoxy-${substr(each.key, 0, 24)}"
  display_name = "${title(each.key)}${var.connector_display_name_suffix} via Psoxy"
}

module "connector-oauth" {
  for_each = local.long_access_parameters

  source = "../../modules/gcp-oauth-secrets"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-oauth-secrets?ref=v0.4.10"

  secret_name           = "PSOXY_${upper(replace(each.value.connector_name, "-", "_"))}_${upper(each.value.secret_name)}"
  project_id            = var.gcp_project_id
  service_account_email = google_service_account.long_auth_connector_sa[each.value.connector_name].email
}

module "long-auth-token-secret-fill-instructions" {
  for_each = local.long_access_parameters

  source = "../../modules/gcp-secret-fill-md"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-secret-fill-md?ref=v0.4.10"

  project_id = var.gcp_project_id
  secret_id  = module.connector-oauth[each.key].secret_id
}

module "source_token_external_todo" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors_todos

  source = "../../modules/source-token-external-todo"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/source-token-external-todo?ref=v0.4.10"

  source_id                         = each.key
  connector_specific_external_steps = each.value.external_token_todo
  todo_step                         = 1

  additional_steps = [for parameter_ref in local.long_access_parameters_by_connector[each.key] : module.long-auth-token-secret-fill-instructions[parameter_ref].todo_markdown]
}

module "connector-long-auth-function" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  source = "../../modules/gcp-psoxy-rest"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-psoxy-rest?ref=v0.4.10"

  project_id                    = var.gcp_project_id
  source_kind                   = each.value.source_kind
  instance_id                   = "psoxy-${each.key}"
  service_account_email         = google_service_account.long_auth_connector_sa[each.key].email
  artifacts_bucket_name         = module.psoxy-gcp.artifacts_bucket_name
  deployment_bundle_object_name = module.psoxy-gcp.deployment_bundle_object_name
  path_to_config                = "${local.base_config_path}${each.value.source_kind}.yaml"
  path_to_repo_root             = var.psoxy_base_dir
  example_api_calls             = each.value.example_api_calls
  todo_step                     = module.source_token_external_todo[each.key].next_todo_step
  secret_bindings               = module.psoxy-gcp.secrets

  environment_variables = merge(
    var.general_environment_variables,
    try(each.value.environment_variables, {}),
    {
      IS_DEVELOPMENT_MODE = contains(var.non_production_connectors, each.key)
    }
  )
}


# NOTE: ACCESS_TOKEN, ENCRYPTION_KEY not passed via secret_bindings (which would get bound as
# env vars at function start-up) because
#   - to be bound as env vars, secrets must already exist or function fails to start (w/o any
#     error visible to Terraform other than timeout); ACCESS_TOKEN may need to be created manually
#     so may not be defined at time of provisioning, and ENCRYPTION_KEY is optional
#   - both ACCESS_TOKEN, ENCRYPTION_KEY may be subject to rotation outside of terraform; no easy
#     way for users to force function restart, and env vars won't reload value of a secret until
#     function restarts. Better to let app-code load these values from Secret Manager, cache with
#     a TTL, and periodically refresh or refresh on auth errors.
module "worklytics-psoxy-connection-long-auth" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  source = "../../modules/worklytics-psoxy-connection"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection?ref=v0.4.10"

  psoxy_host_platform_id = "GCP"
  psoxy_instance_id      = each.key
  connector_id           = try(each.value.worklytics_connector_id, "")
  psoxy_endpoint_url     = module.connector-long-auth-function[each.key].cloud_function_url
  display_name           = "${each.value.display_name} via Psoxy${var.connector_display_name_suffix}"
  todo_step              = module.connector-long-auth-function[each.key].next_todo_step
}
# END LONG ACCESS AUTH CONNECTORS

# BEGIN MSFT Connectors


resource "google_service_account" "msft-connector-sa" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  project      = var.gcp_project_id
  account_id   = "psoxy-${substr(each.key, 0, 24)}"
  display_name = "Psoxy Connector - ${each.value.display_name}${var.connector_display_name_suffix}"
  description  = "Service account for psoxy Connector - ${each.value.display_name}${var.connector_display_name_suffix}"

  depends_on = [
    module.psoxy-gcp
  ]
}

module "msft-connection-auth-federation" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  source = "../../modules/azuread-federated-credentials"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-local-cert?ref=v0.4.8"

  application_object_id = module.msft-connection[each.key].connector.id
  display_name = "GcpFederation"
  description = "Federation to be used for psoxy Connector - ${each.value.display_name}${var.connector_display_name_suffix}"
  issuer = "https://accounts.google.com"
  subject = google_service_account.msft-connector-sa[each.key].unique_id
}

module "msft-connection" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  source = "../../modules/azuread-connection"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-connection?ref=v0.4.10"

  display_name                      = "Psoxy Connector - ${each.value.display_name}${var.connector_display_name_suffix}"
  tenant_id                         = var.msft_tenant_id
  required_app_roles                = each.value.required_app_roles
  required_oauth2_permission_scopes = each.value.required_oauth2_permission_scopes
}

module "psoxy-msft-connector" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  source = "../../modules/gcp-psoxy-rest"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-psoxy-rest?ref=v0.4.10"

  project_id                            = var.gcp_project_id
  source_kind                           = each.value.source_kind
  instance_id                           = "psoxy-${each.key}"
  service_account_email                 = google_service_account.msft-connector-sa[each.key].email
  artifacts_bucket_name                 = module.psoxy-gcp.artifacts_bucket_name
  deployment_bundle_object_name         = module.psoxy-gcp.deployment_bundle_object_name
  path_to_config                        = "${local.base_config_path}${each.value.source_kind}.yaml"
  path_to_repo_root                     = var.psoxy_base_dir
  example_api_calls                     = each.value.example_calls
  todo_step                             = 42

  environment_variables = merge(
    var.general_environment_variables,
    try(each.value.environment_variables, {}),
    {
      CLIENT_ID = module.msft-connection[each.key].connector.application_id
      TENANT_ID = var.msft_tenant_id
      AUDIENCE = module.msft-connection-auth-federation[each.key].audience
      IS_DEVELOPMENT_MODE = contains(var.non_production_connectors, each.key)
    }
  )

  secret_bindings = module.psoxy-gcp.secrets
}


# END MSFT Connectors

# BEGIN BULK CONNECTORS
module "psoxy-gcp-bulk" {
  for_each = merge(module.worklytics_connector_specs.enabled_bulk_connectors,
  var.custom_bulk_connectors)

  source = "../../modules/gcp-psoxy-bulk"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-psoxy-bulk?ref=v0.4.10"

  project_id                    = var.gcp_project_id
  worklytics_sa_emails          = var.worklytics_sa_emails
  region                        = var.gcp_region
  source_kind                   = each.value.source_kind
  artifacts_bucket_name         = module.psoxy-gcp.artifacts_bucket_name
  deployment_bundle_object_name = module.psoxy-gcp.deployment_bundle_object_name
  psoxy_base_dir                = var.psoxy_base_dir
  bucket_write_role_id          = module.psoxy-gcp.bucket_write_role_id
  secret_bindings               = module.psoxy-gcp.secrets

  environment_variables = merge(
    var.general_environment_variables,
    try(each.value.environment_variables, {}),
    {
      SOURCE              = each.value.source_kind
      RULES               = yamlencode(each.value.rules)
      IS_DEVELOPMENT_MODE = contains(var.non_production_connectors, each.key)
    }
  )
}

module "psoxy-bulk-to-worklytics" {
  for_each = merge(module.worklytics_connector_specs.enabled_bulk_connectors,
  var.custom_bulk_connectors)

  source = "../../modules/worklytics-psoxy-connection-generic"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection-generic?ref=v0.4.10"

  psoxy_host_platform_id = local.host_platform_id
  psoxy_instance_id      = each.key
  connector_id           = try(each.value.worklytics_connector_id, "")
  display_name           = try(each.value.worklytics_connector_name, "${each.value.display_name} via Psoxy")
  todo_step              = module.psoxy-gcp-bulk[each.key].next_todo_step

  settings_to_provide = merge({
    "Bucket Name" = module.psoxy-gcp-bulk[each.key].sanitized_bucket
  }, try(each.value.settings_to_provide, {}))
}

locals {
  all_instances = merge(
    { for instance in module.psoxy-google-workspace-connector : instance.instance_id => instance },
    { for instance in module.psoxy-gcp-bulk : instance.instance_id => instance },
    { for instance in module.connector-long-auth-function : instance.instance_id => instance }
  )
}

output "instances" {
  value = ""
}