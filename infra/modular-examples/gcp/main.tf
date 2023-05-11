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

  config_parameter_prefix               = var.config_parameter_prefix == "" ? "${var.environment_id}_" : var.config_parameter_prefix
  environment_id_prefix                 = "${var.environment_id}${length(var.environment_id) > 0 ? "-" : ""}"
  environment_id_display_name_qualifier = length(var.environment_id) > 0 ? " ${var.environment_id} " : ""
}

module "worklytics_connector_specs" {
  source = "../../modules/worklytics-connector-specs"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-connector-specs?ref=v0.4.22

  enabled_connectors             = var.enabled_connectors
  google_workspace_example_user  = var.google_workspace_example_user
  google_workspace_example_admin = try(coalesce(var.google_workspace_example_admin, var.google_workspace_example_user), null)
  salesforce_domain              = var.salesforce_domain
  msft_tenant_id                 = var.msft_tenant_id
}

module "psoxy" {
  source = "../../modules/gcp"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp?ref=v0.4.22"

  project_id              = var.gcp_project_id
  environment_id_prefix   = local.environment_id_prefix
  psoxy_base_dir          = var.psoxy_base_dir
  force_bundle            = var.force_bundle
  bucket_location         = var.gcp_region
  config_parameter_prefix = local.config_parameter_prefix
  install_test_tool       = var.install_test_tool
}

module "google_workspace_connection" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/google-workspace-dwd-connection"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/google-workspace-dwd-connection?ref=v0.4.22"

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

module "google_workspace_connection_auth" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/gcp-sa-auth-key"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-sa-auth-key?ref=v0.4.22"

  service_account_id = module.google_workspace_connection[each.key].service_account_id
}

module "google_workspace_key_secrets" {

  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/gcp-secrets"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-secrets?ref=v0.4.22"

  secret_project = var.gcp_project_id
  path_prefix    = local.config_parameter_prefix
  secrets = {
    "${replace(upper(each.key), "-", "_")}_SERVICE_ACCOUNT_KEY" : {
      value       = module.google_workspace_connection_auth[each.key].key_value
      description = "Auth key for ${each.key} service account"
    }
  }
}

module "psoxy_google_workspace_connector" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/gcp-psoxy-rest"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-psoxy-rest?ref=v0.4.22"

  project_id                            = var.gcp_project_id
  source_kind                           = each.value.source_kind
  environment_id_prefix                 = local.environment_id_prefix
  instance_id                           = each.key
  service_account_email                 = module.google_workspace_connection[each.key].service_account_email
  artifacts_bucket_name                 = module.psoxy.artifacts_bucket_name
  deployment_bundle_object_name         = module.psoxy.deployment_bundle_object_name
  path_to_config                        = null
  path_to_repo_root                     = var.psoxy_base_dir
  example_api_calls                     = each.value.example_api_calls
  example_api_calls_user_to_impersonate = each.value.example_api_calls_user_to_impersonate
  todo_step                             = module.google_workspace_connection[each.key].next_todo_step
  target_host                           = each.value.target_host
  source_auth_strategy                  = each.value.source_auth_strategy
  oauth_scopes                          = try(each.value.oauth_scopes_needed, [])
  config_parameter_prefix               = local.config_parameter_prefix
  invoker_sa_emails                     = var.worklytics_sa_emails


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
      secret_id      = module.google_workspace_key_secrets[each.key].secret_ids_within_project["${replace(upper(each.key), "-", "_")}_SERVICE_ACCOUNT_KEY"]
      version_number = module.google_workspace_key_secrets[each.key].secret_version_numbers["${replace(upper(each.key), "-", "_")}_SERVICE_ACCOUNT_KEY"]
    }
  }, module.psoxy.secrets)
}

module "worklytics_psoxy_connection" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/worklytics-psoxy-connection"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection?ref=v0.4.22"

  psoxy_host_platform_id = local.host_platform_id
  psoxy_instance_id      = each.key
  connector_id           = try(each.value.worklytics_connector_id, "")
  psoxy_endpoint_url     = module.psoxy_google_workspace_connector[each.key].cloud_function_url
  display_name           = "${title(each.key)}${local.environment_id_display_name_qualifier} via Psoxy"
  todo_step              = module.psoxy_google_workspace_connector[each.key].next_todo_step
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

module "connector_oauth" {
  for_each = local.long_access_parameters

  source = "../../modules/gcp-oauth-secrets"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-oauth-secrets?ref=v0.4.22"

  project_id            = var.gcp_project_id
  path_prefix           = local.config_parameter_prefix
  secret_name           = "${upper(replace(each.value.connector_name, "-", "_"))}_${upper(each.value.secret_name)}"
  service_account_email = google_service_account.long_auth_connector_sa[each.value.connector_name].email
}

module "long_auth_token_secret_fill_instructions" {
  for_each = local.long_access_parameters

  source = "../../modules/gcp-secret-fill-md"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-secret-fill-md?ref=v0.4.22"

  project_id  = var.gcp_project_id
  path_prefix = local.config_parameter_prefix
  secret_id   = module.connector_oauth[each.key].secret_id
}

module "source_token_external_todo" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors_todos

  source = "../../modules/source-token-external-todo"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/source-token-external-todo?ref=v0.4.22"

  source_id                         = each.key
  connector_specific_external_steps = each.value.external_token_todo
  todo_step                         = 1

  additional_steps = [for parameter_ref in local.long_access_parameters_by_connector[each.key] : module.long_auth_token_secret_fill_instructions[parameter_ref].todo_markdown]
}

module "connector_long_auth_function" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  source = "../../modules/gcp-psoxy-rest"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-psoxy-rest?ref=v0.4.22"

  project_id                    = var.gcp_project_id
  environment_id_prefix         = local.environment_id_prefix
  source_kind                   = each.value.source_kind
  instance_id                   = each.key
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
  invoker_sa_emails             = var.worklytics_sa_emails

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
module "worklytics_psoxy_connection_long_auth" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  source = "../../modules/worklytics-psoxy-connection"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection?ref=v0.4.22"

  psoxy_host_platform_id = "GCP"
  psoxy_instance_id      = each.key
  connector_id           = try(each.value.worklytics_connector_id, "")
  psoxy_endpoint_url     = module.connector_long_auth_function[each.key].cloud_function_url
  display_name           = "${each.value.display_name} via Psoxy${local.environment_id_display_name_qualifier}"
  todo_step              = module.connector_long_auth_function[each.key].next_todo_step
}
# END LONG ACCESS AUTH CONNECTORS


module "custom_rest_rules" {
  source = "../../modules/gcp-sm-rules"

  for_each = var.custom_rest_rules

  prefix    = "${local.config_parameter_prefix}${upper(replace(each.key, "-", "_"))}_"
  file_path = each.value
}


# BEGIN BULK CONNECTORS
module "psoxy_bulk" {
  for_each = merge(module.worklytics_connector_specs.enabled_bulk_connectors,
  var.custom_bulk_connectors)

  source = "../../modules/gcp-psoxy-bulk"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-psoxy-bulk?ref=v0.4.22"

  project_id                    = var.gcp_project_id
  environment_id_prefix         = local.environment_id_prefix
  instance_id                   = each.key
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

module "psoxy_bulk_to_worklytics" {
  for_each = merge(module.worklytics_connector_specs.enabled_bulk_connectors,
  var.custom_bulk_connectors)

  source = "../../modules/worklytics-psoxy-connection-generic"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection-generic?ref=v0.4.22"

  psoxy_host_platform_id = local.host_platform_id
  psoxy_instance_id      = each.key
  connector_id           = try(each.value.worklytics_connector_id, "")
  display_name           = try(each.value.worklytics_connector_name, "${each.value.display_name} via Psoxy")
  todo_step              = module.psoxy_bulk[each.key].next_todo_step

  settings_to_provide = merge({
    "Bucket Name" = module.psoxy_bulk[each.key].sanitized_bucket
  }, try(each.value.settings_to_provide, {}))
}

# END BULK CONNECTORS

# BEGIN LOOKUP TABLES
module "lookup_output" {
  for_each = var.lookup_tables

  source = "../../modules/gcp-output-bucket"

  bucket_write_role_id           = module.psoxy.bucket_write_role_id
  function_service_account_email = module.psoxy_bulk[each.value.source_connector_id].instance_sa_email
  project_id                     = var.gcp_project_id
  region                         = var.gcp_region
  bucket_name_prefix             = module.psoxy_bulk[each.value.source_connector_id].bucket_prefix
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

  project   = var.gcp_project_id
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

resource "google_secret_manager_secret_iam_member" "additional_transforms" {
  for_each = local.inputs_to_build_lookups_for

  secret_id = google_secret_manager_secret.additional_transforms[each.key].id
  member    = "serviceAccount:${module.psoxy_bulk[each.key].instance_sa_email}"
  role      = "roles/secretmanager.secretAccessor"
}

# END LOOKUP TABLES

locals {
  all_instances = merge(
    { for instance in module.psoxy_google_workspace_connector : instance.instance_id => instance },
    { for instance in module.psoxy_bulk : instance.instance_id => instance },
    { for instance in module.connector_long_auth_function : instance.instance_id => instance }
  )
}

