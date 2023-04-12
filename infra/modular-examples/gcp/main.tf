# consider this an *ALPHA* as of release 0.4.18
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

  config_parameter_prefix = var.config_parameter_prefix == "" ? "${var.environment_id}_" : var.config_parameter_prefix
  environment_id_prefix   = "psoxy-${var.environment_id}${length(var.environment_id) > 0 ? "-" : ""}"
  environment_id_display_name_qualifier = length(var.environment_id) > 0 ? " ${var.environment_id} " : ""
}

module "worklytics_connector_specs" {
  source = "../../modules/worklytics-connector-specs"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-connector-specs?ref=v0.4.18"

  enabled_connectors             = var.enabled_connectors
  google_workspace_example_user  = var.google_workspace_example_user
  google_workspace_example_admin = coalesce(var.google_workspace_example_admin, var.google_workspace_example_user)
  salesforce_domain              = var.salesforce_domain
}

module "psoxy" {
  source = "../../modules/gcp"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp?ref=v0.4.18"

  project_id              = var.gcp_project_id
  environment_id_prefix   = local.environment_id_prefix
  psoxy_base_dir          = var.psoxy_base_dir
  force_bundle            = var.force_bundle
  bucket_location         = var.gcp_region
  invoker_sa_emails       = var.worklytics_sa_emails
  config_parameter_prefix = local.config_parameter_prefix
}

module "google-workspace-connection" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/google-workspace-dwd-connection"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/google-workspace-dwd-connection?ref=v0.4.18"

  project_id                   = var.gcp_project_id
  connector_service_account_id = "${local.environment_id_prefix}${substr(each.key, 0, 30 - length(local.environment_id_prefix))}"
  display_name                 = "Psoxy Connector - ${local.environment_id_display_name_qualifier}${each.value.display_name}"
  apis_consumed                = each.value.apis_consumed
  oauth_scopes_needed          = each.value.oauth_scopes_needed
  todo_step                    = 1

  depends_on = [
    module.psoxy
  ]
}

module "google-workspace-connection-auth" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/gcp-sa-auth-key"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-sa-auth-key?ref=v0.4.18"

  service_account_id = module.google-workspace-connection[each.key].service_account_id
}

module "google-workspace-key-secrets" {

  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/gcp-secrets"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-secrets?ref=v0.4.18"

  secret_project = var.gcp_project_id
  path_prefix    = local.config_parameter_prefix
  secrets = {
    "PSOXY_${replace(upper(each.key), "-", "_")}_SERVICE_ACCOUNT_KEY" : {
      value       = module.google-workspace-connection-auth[each.key].key_value
      description = "Auth key for ${each.key} service account"
    }
  }
}

module "psoxy-google-workspace-connector" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/gcp-psoxy-rest"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-psoxy-rest?ref=v0.4.18"

  project_id                            = var.gcp_project_id
  source_kind                           = each.value.source_kind
  instance_id                           = "${local.environment_id_prefix}${each.key}"
  service_account_email                 = module.google-workspace-connection[each.key].service_account_email
  artifacts_bucket_name                 = module.psoxy.artifacts_bucket_name
  deployment_bundle_object_name         = module.psoxy.deployment_bundle_object_name
  path_to_config                        = null
  path_to_repo_root                     = var.psoxy_base_dir
  example_api_calls                     = each.value.example_api_calls
  example_api_calls_user_to_impersonate = each.value.example_api_calls_user_to_impersonate
  todo_step                             = module.google-workspace-connection[each.key].next_todo_step
  target_host                           = each.value.target_host
  source_auth_strategy                  = each.value.source_auth_strategy
  oauth_scopes                          = try(each.value.oauth_scopes_needed, [])
  config_parameter_prefix               = local.config_parameter_prefix


  environment_variables = merge(
    var.general_environment_variables,
    try(each.value.environment_variables, {}),
    {
      BUNDLE_FILENAME      = module.psoxy.filename
      IS_DEVELOPMENT_MODE  = contains(var.non_production_connectors, each.key)
      PSEUDONYMIZE_APP_IDS = tostring(var.pseudonymize_app_ids)
      CUSTOM_RULES_SHA     = try(var.custom_rest_rules[each.key], null) != null ? filesha1(var.custom_rest_rules[each.key]) : null
    }
  )

  secret_bindings = merge({
    # as SERVICE_ACCOUNT_KEY rotated by Terraform, reasonable to bind as env variable
    SERVICE_ACCOUNT_KEY = {
      secret_id      = module.google-workspace-key-secrets[each.key].secret_ids_within_project["PSOXY_${replace(upper(each.key), "-", "_")}_SERVICE_ACCOUNT_KEY"]
      version_number = module.google-workspace-key-secrets[each.key].secret_version_numbers["PSOXY_${replace(upper(each.key), "-", "_")}_SERVICE_ACCOUNT_KEY"]
    }
  }, module.psoxy.secrets)
}

module "worklytics-psoxy-connection" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/worklytics-psoxy-connection"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection?ref=v0.4.18"

  psoxy_host_platform_id = local.host_platform_id
  psoxy_instance_id      = each.key
  connector_id           = try(each.value.worklytics_connector_id, "")
  psoxy_endpoint_url     = module.psoxy-google-workspace-connector[each.key].cloud_function_url
  display_name           = "${title(each.key)}${local.environment_id_display_name_qualifier} via Psoxy"
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
  account_id   = "${local.environment_id_prefix}${substr(each.key, 0, 30 - length(local.environment_id_prefix))}"
  display_name = "${title(each.key)}${local.environment_id_display_name_qualifier} via Psoxy"
}

module "connector-oauth" {
  for_each = local.long_access_parameters

  source = "../../modules/gcp-oauth-secrets"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-oauth-secrets?ref=v0.4.18"

  project_id            = var.gcp_project_id
  path_prefix           = local.config_parameter_prefix
  secret_name           = "PSOXY_${upper(replace(each.value.connector_name, "-", "_"))}_${upper(each.value.secret_name)}"
  service_account_email = google_service_account.long_auth_connector_sa[each.value.connector_name].email
}

module "long-auth-token-secret-fill-instructions" {
  for_each = local.long_access_parameters

  source = "../../modules/gcp-secret-fill-md"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-secret-fill-md?ref=v0.4.18"

  project_id  = var.gcp_project_id
  path_prefix = local.config_parameter_prefix
  secret_id   = module.connector-oauth[each.key].secret_id
}

module "source_token_external_todo" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors_todos

  source = "../../modules/source-token-external-todo"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/source-token-external-todo?ref=v0.4.18"

  source_id                         = each.key
  connector_specific_external_steps = each.value.external_token_todo
  todo_step                         = 1

  additional_steps = [for parameter_ref in local.long_access_parameters_by_connector[each.key] : module.long-auth-token-secret-fill-instructions[parameter_ref].todo_markdown]
}

module "connector-long-auth-function" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  source = "../../modules/gcp-psoxy-rest"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-psoxy-rest?ref=v0.4.18"

  project_id                    = var.gcp_project_id
  source_kind                   = each.value.source_kind
  instance_id                   = "${local.environment_id_prefix}${each.key}"
  service_account_email         = google_service_account.long_auth_connector_sa[each.key].email
  artifacts_bucket_name         = module.psoxy.artifacts_bucket_name
  deployment_bundle_object_name = module.psoxy.deployment_bundle_object_name
  path_to_repo_root             = var.psoxy_base_dir
  example_api_calls             = each.value.example_api_calls
  todo_step                     = module.source_token_external_todo[each.key].next_todo_step
  secret_bindings               = module.psoxy.secrets
  target_host                   = each.value.target_host
  source_auth_strategy          = each.value.source_auth_strategy
  oauth_scopes                  = try(each.value.oauth_scopes_needed, [])
  config_parameter_prefix       = local.config_parameter_prefix

  environment_variables = merge(
    var.general_environment_variables,
    try(each.value.environment_variables, {}),
    {
      BUNDLE_FILENAME      = module.psoxy.filename
      PSEUDONYMIZE_APP_IDS = tostring(var.pseudonymize_app_ids)
      IS_DEVELOPMENT_MODE  = contains(var.non_production_connectors, each.key)
      CUSTOM_RULES_SHA     = try(var.custom_rest_rules[each.key], null) != null ? filesha1(var.custom_rest_rules[each.key]) : null
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
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection?ref=v0.4.18"

  psoxy_host_platform_id = "GCP"
  psoxy_instance_id      = each.key
  connector_id           = try(each.value.worklytics_connector_id, "")
  psoxy_endpoint_url     = module.connector-long-auth-function[each.key].cloud_function_url
  display_name           = "${each.value.display_name} via Psoxy${local.environment_id_display_name_qualifier}"
  todo_step              = module.connector-long-auth-function[each.key].next_todo_step
}
# END LONG ACCESS AUTH CONNECTORS


module "custom_rest_rules" {
  source = "../../modules/gcp-sm-rules"

  for_each = var.custom_rest_rules

  prefix    = "${local.config_parameter_prefix}PSOXY_${upper(replace(each.key, "-", "_"))}_"
  file_path = each.value
}


# BEGIN BULK CONNECTORS
module "psoxy-bulk" {
  for_each = merge(module.worklytics_connector_specs.enabled_bulk_connectors,
    var.custom_bulk_connectors)

  source = "../../modules/gcp-psoxy-bulk"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-psoxy-bulk?ref=v0.4.18"

  project_id                    = var.gcp_project_id
  environment_id_prefix         = local.environment_id_prefix
  worklytics_sa_emails          = var.worklytics_sa_emails
  config_parameter_prefix       = local.config_parameter_prefix
  region                        = var.gcp_region
  source_kind                   = each.value.source_kind
  artifacts_bucket_name         = module.psoxy.artifacts_bucket_name
  deployment_bundle_object_name = module.psoxy.deployment_bundle_object_name
  psoxy_base_dir                = var.psoxy_base_dir
  bucket_write_role_id          = module.psoxy.bucket_write_role_id
  secret_bindings               = module.psoxy.secrets
  example_file                  = try(each.value.example_file, null)
  input_expiration_days         = var.bulk_input_expiration_days
  sanitized_expiration_days     = var.bulk_sanitized_expiration_days


  environment_variables = merge(
    var.general_environment_variables,
    try(each.value.environment_variables, {}),
    {
      SOURCE              = each.value.source_kind
      RULES               = yamlencode(each.value.rules)
      BUNDLE_FILENAME     = module.psoxy.filename
      IS_DEVELOPMENT_MODE = contains(var.non_production_connectors, each.key)
    }
  )
}

module "psoxy-bulk-to-worklytics" {
  for_each = merge(module.worklytics_connector_specs.enabled_bulk_connectors,
    var.custom_bulk_connectors)

  source = "../../modules/worklytics-psoxy-connection-generic"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection-generic?ref=v0.4.18"

  psoxy_host_platform_id = local.host_platform_id
  psoxy_instance_id      = each.key
  connector_id           = try(each.value.worklytics_connector_id, "")
  display_name           = try(each.value.worklytics_connector_name, "${each.value.display_name} via Psoxy")
  todo_step              = module.psoxy-bulk[each.key].next_todo_step

  settings_to_provide = merge({
    "Bucket Name" = module.psoxy-bulk[each.key].sanitized_bucket
  }, try(each.value.settings_to_provide, {}))
}

# END BULK CONNECTORS

# BEGIN LOOKUP TABLES
module "lookup_output" {
  for_each = var.lookup_tables

  source = "../../modules/gcp-output-bucket"

  bucket_write_role_id           = module.psoxy.bucket_write_role_id
  function_service_account_email = module.psoxy-bulk[each.value.source_connector_id].instance_sa_email
  project_id                     = var.gcp_project_id
  region                         = var.gcp_region
  bucket_name_prefix             = module.psoxy-bulk[each.value.source_connector_id].bucket_prefix
  bucket_name_suffix             = "-lookup" # TODO: what if multiple lookups from same source??
  expiration_days                = each.value.expiration_days
  sanitizer_accessor_principals  = each.value.sanitized_accessor_principals
}

locals {
  inputs_to_build_lookups_for = toset(distinct([for k, v in var.lookup_tables : v.source_connector_id]))
}

# TODO: this would be cleaner as env var, but creates a cycle:
# Error: Cycle: module.psoxy.module.psoxy-bulk.local_file.todo-gcp-psoxy-bulk-test, module.psoxy.module.lookup_output.var.function_service_account_email (expand), module.psoxy.module.lookup_output.google_storage_bucket_iam_member.write_to_output_bucket, module.psoxy.module.lookup_output.output.bucket_name (expand), module.psoxy.module.lookup_output.var.bucket_name_prefix (expand), module.psoxy.module.lookup_output.google_storage_bucket.bucket, module.psoxy.module.lookup_output.google_storage_bucket_iam_member.accessors, module.psoxy.module.lookup_output (close), module.psoxy.module.psoxy-bulk.var.environment_variables (expand), module.psoxy.module.psoxy-bulk.google_cloudfunctions_function.function, module.psoxy.module.psoxy-bulk (close)
resource "google_secret_manager_secret" "additional_transforms" {
  for_each = local.inputs_to_build_lookups_for

  secret_id = "${local.config_parameter_prefix}${upper(replace(each.key, "-", "_"))}_ADDITIONAL_TRANSFORMS"

  replication {
    automatic = true
  }
}

resource "google_secret_manager_secret_version" "additional_transforms" {
  for_each = local.inputs_to_build_lookups_for

  secret = google_secret_manager_secret.additional_transforms[each.key].name
  secret_data = yamlencode([
    for k, v in var.lookup_tables : {
      destinationBucketName : module.lookup_output[k].bucket_name
      rules : {
        columnsToDuplicate : {
          (v.join_key_column) : "${v.join_key_column}_pseudonym"
        },
        columnsToPseudonymize : ["${v.join_key_column}_pseudonym"]
        columnsToInclude : try(concat(v.columns_to_include, [
          v.join_key_column, "${v.join_key_column}_pseudonym"
        ]), null)
      }
    } if v.source_connector_id == each.key
  ])
}

# END LOOKUP TABLES

locals {
  all_instances = merge(
    { for instance in module.psoxy-google-workspace-connector : instance.instance_id => instance },
    { for instance in module.psoxy-bulk : instance.instance_id => instance },
    { for instance in module.connector-long-auth-function : instance.instance_id => instance }
  )
}

output "instances" {
  description = "Instances of Psoxy connectors deployments as Cloud Functions."
  value       = local.all_instances
}

output "todos_1" {
  description = "List of todo steps to complete 1st, in markdown format."
  value       = concat(
    values(module.google-workspace-connection)[*].todo,
    values(module.source_token_external_todo)[*].todo,
  )
}

output "todos_2" {
  description = "List of todo steps to complete 2nd, in markdown format."
  value       = concat(
    values(module.psoxy-google-workspace-connector)[*].todo,
    values(module.connector-long-auth-function)[*].todo,
    values(module.psoxy-bulk)[*].todo,
  )
}

output "todos_3" {
  description = "List of todo steps to complete 3rd, in markdown format."
  value       = concat(
    values(module.worklytics-psoxy-connection)[*].todo,
    values(module.worklytics-psoxy-connection)[*].todo,
    values(module.psoxy-bulk-to-worklytics)[*].todo,
  )
}


# use case: let someone consume this deploy another psoxy instance, reusing artifacts
output "artifacts_bucket_name" {
  description = "Name of GCS bucket with deployment artifacts."
  value       = module.psoxy.artifacts_bucket_name
}

output "deployment_bundle_object_name" {
  description = "Object name of deployment bundle within artifacts bucket."
  value       = module.psoxy.deployment_bundle_object_name
}
