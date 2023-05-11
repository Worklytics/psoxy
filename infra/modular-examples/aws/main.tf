terraform {
  required_providers {
    # for the infra that will host Psoxy instances
    aws = {
      source  = "hashicorp/aws"
      version = ">= 4.12"
    }
  }
}

# deployment ID to avoid collisions if deploying host environment (AWS account, GCP project) that
# is shared by multiple deployments
resource "random_string" "deployment_id" {
  length  = 5
  lower   = true
  upper   = false
  numeric = true
  special = false
}

locals {
  base_config_path = "${var.psoxy_base_dir}/configs/"
  host_platform_id = "AWS"
  ssm_key_ids      = var.aws_ssm_key_id == null ? {} : { 0 : var.aws_ssm_key_id }
  deployment_id    = length(var.environment_name) > 0 ? replace(lower(var.environment_name), " ", "-") : random_string.deployment_id.result
  proxy_brand      = "psoxy"
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

module "psoxy_aws" {
  source = "../../modules/aws"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws?ref=v0.4.22

  aws_account_id                 = var.aws_account_id
  psoxy_base_dir                 = var.psoxy_base_dir
  caller_aws_arns                = var.caller_aws_arns
  caller_gcp_service_account_ids = var.caller_gcp_service_account_ids
  force_bundle                   = var.force_bundle
  install_test_tool              = var.install_test_tool
  deployment_id                  = length(var.environment_name) > 0 ? var.environment_name : "Psoxy"
}

# TODO: remove in v0.5
moved {
  from = module.psoxy-aws
  to   = module.psoxy_aws
}

# secrets shared across all instances
module "global_secrets" {
  source = "../../modules/aws-ssm-secrets"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-ssm-secrets?ref=v0.4.22

  path       = var.aws_ssm_param_root_path
  kms_key_id = var.aws_ssm_key_id
  secrets    = module.psoxy_aws.secrets
}

locals {
  deployment_id_sa_id_part = length(local.deployment_id) > 0 ? "${local.deployment_id}-" : ""
}

# BEGIN GOOGLE WORKSPACE CONNECTORS

module "google_workspace_connection" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/google-workspace-dwd-connection"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/google-workspace-dwd-connection?ref=v0.4.22

  project_id                   = var.gcp_project_id
  connector_service_account_id = "${local.proxy_brand}-${local.deployment_id_sa_id_part}${each.key}"
  display_name                 = "Psoxy Connector - ${each.value.display_name}${var.connector_display_name_suffix}"
  apis_consumed                = each.value.apis_consumed
  oauth_scopes_needed          = each.value.oauth_scopes_needed
  todo_step                    = 1

  depends_on = [
    module.psoxy_aws
  ]
}

# TODO: remove in v0.5
moved {
  from = module.google-workspace-connection
  to   = module.google_workspace_connection
}


module "google_workspace_connection_auth" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/gcp-sa-auth-key"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-sa-auth-key?ref=v0.4.22"

  service_account_id = module.google_workspace_connection[each.key].service_account_id
}

# TODO: remove in v0.5
moved {
  from = module.google-workspace-connection-auth
  to   = module.google_workspace_connection_auth
}


module "sa_key_secrets" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/aws-ssm-secrets"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-ssm-secrets?ref=v0.4.22"
  # other possibly implementations:
  # source = "../hashicorp-vault-secrets"

  path       = var.aws_ssm_param_root_path
  kms_key_id = var.aws_ssm_key_id

  secrets = {
    "PSOXY_${replace(upper(each.key), "-", "_")}_SERVICE_ACCOUNT_KEY" : {
      value       = module.google_workspace_connection_auth[each.key].key_value
      description = "GCP service account key for ${each.key} connector"
    }
  }
}

# TODO: remove in v0.5
moved {
  from = module.sa-key-secrets
  to   = module.sa_key_secrets
}


module "psoxy_google_workspace_connector" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/aws-psoxy-rest"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-rest?ref=v0.4.22"

  function_name                         = "psoxy-${each.key}"
  source_kind                           = each.key
  path_to_function_zip                  = module.psoxy_aws.path_to_deployment_jar
  function_zip_hash                     = module.psoxy_aws.deployment_package_hash
  path_to_config                        = null
  api_caller_role_arn                   = module.psoxy_aws.api_caller_role_arn
  aws_assume_role_arn                   = var.aws_assume_role_arn
  aws_account_id                        = var.aws_account_id
  path_to_repo_root                     = var.psoxy_base_dir
  example_api_calls                     = each.value.example_api_calls
  example_api_calls_user_to_impersonate = each.value.example_api_calls_user_to_impersonate
  global_parameter_arns                 = module.global_secrets.secret_arns
  path_to_instance_ssm_parameters       = "${var.aws_ssm_param_root_path}PSOXY_${upper(replace(each.key, "-", "_"))}_"
  ssm_kms_key_ids                       = local.ssm_key_ids
  target_host                           = each.value.target_host
  source_auth_strategy                  = each.value.source_auth_strategy
  oauth_scopes                          = try(each.value.oauth_scopes_needed, [])

  todo_step = module.google_workspace_connection[each.key].next_todo_step

  environment_variables = merge(
    var.general_environment_variables,
    try(each.value.environment_variables, {}),
    {
      PSEUDONYMIZE_APP_IDS = tostring(var.pseudonymize_app_ids)
      IS_DEVELOPMENT_MODE  = contains(var.non_production_connectors, each.key)
    }
  )
}

# TODO: remove in v0.5
moved {
  from = module.psoxy-google-workspace-connector
  to   = module.psoxy_google_workspace_connector
}

module "worklytics_psoxy_connection_google_workspace" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/worklytics-psoxy-connection"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection?ref=v0.4.22"

  psoxy_instance_id      = each.key
  psoxy_host_platform_id = local.host_platform_id
  connector_id           = try(each.value.worklytics_connector_id, "")
  psoxy_endpoint_url     = module.psoxy_google_workspace_connector[each.key].endpoint_url
  display_name           = "${each.value.display_name} via Psoxy${var.connector_display_name_suffix}"
  todo_step              = module.psoxy_google_workspace_connector[each.key].next_todo_step

  settings_to_provide = {
    "AWS Psoxy Region"   = var.aws_region,
    "AWS Psoxy Role ARN" = module.psoxy_aws.api_caller_role_arn
  }
}

# TODO: remove in v0.5
moved {
  from = module.worklytics-psoxy-connection-google-workspace
  to   = module.worklytics_psoxy_connection_google_workspace
}

# END GOOGLE WORKSPACE CONNECTORS

# BEGIN MSFT-365 CONNECTORS

locals {
  msft_365_enabled = length(module.worklytics_connector_specs.enabled_msft_365_connectors) > 0
}


module "cognito_identity_pool" {
  count = local.msft_365_enabled ? 1 : 0 # only provision identity pool if MSFT-365 connectors are enabled

  source = "../../modules/aws-cognito-pool"

  developer_provider_name = "azure-access"
  name                    = "azure-ad-federation"
}

module "cognito_identity" {
  count = local.msft_365_enabled ? 1 : 0 # only provision identity pool if MSFT-365 connectors are enabled

  source = "../../modules/aws-cognito-identity-cli"

  identity_pool_id = module.cognito_identity_pool[0].pool_id
  aws_region       = var.aws_region
  login_ids        = { for k in keys(module.msft_connection) : k => "${module.cognito_identity_pool[0].developer_provider_name}=${module.msft_connection[k].connector.application_id}" }
  aws_role         = var.aws_assume_role_arn
}


data "azuread_client_config" "current" {

}

data "azuread_users" "owners" {

  user_principal_names = var.msft_owners_email
}

module "msft_connection" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  source = "../../modules/azuread-connection"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-connection?ref=v0.4.22"

  display_name                      = "Psoxy Connector - ${each.value.display_name}${var.connector_display_name_suffix}"
  tenant_id                         = var.msft_tenant_id
  required_app_roles                = each.value.required_app_roles
  required_oauth2_permission_scopes = each.value.required_oauth2_permission_scopes
  owners                            = toset(concat(data.azuread_users.owners.object_ids, [data.azuread_client_config.current.object_id]))
}

# TODO: remove in v0.5
moved {
  from = module.msft-connection
  to   = module.msft_connection
}

module "msft_connection_auth_federation" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  source = "../../modules/azuread-federated-credentials"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-federated-credentials?ref=v0.4.22"

  application_object_id = module.msft_connection[each.key].connector.id
  display_name          = "AccessFromAWS"
  description           = "AWS federation to be used for psoxy Connector - ${each.value.display_name}${var.connector_display_name_suffix}"
  issuer                = "https://cognito-identity.amazonaws.com"
  audience              = module.cognito_identity_pool[0].pool_id
  subject               = module.cognito_identity[0].identity_id[each.key]
}

# TODO: remove in v0.5
moved {
  from = module.msft-connection-auth-federation
  to   = module.msft_connection_auth_federation
}


# grant required permissions to connectors via Azure AD
# (requires terraform configuration being applied by an Azure User with privileges to do this; it
#  usually requires a 'Global Administrator' for your tenant)
module "msft_365_grants" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  source = "../../modules/azuread-grant-all-users"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-grant-all-users?ref=v0.4.22"

  psoxy_instance_id        = each.key
  application_id           = module.msft_connection[each.key].connector.application_id
  oauth2_permission_scopes = each.value.required_oauth2_permission_scopes
  app_roles                = each.value.required_app_roles
  application_name         = each.key
  todo_step                = 1
}

module "psoxy_msft_connector" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  source = "../../modules/aws-psoxy-rest"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-rest?ref=v0.4.22"

  function_name                   = "psoxy-${each.key}"
  source_kind                     = each.value.source_kind
  path_to_config                  = "${var.psoxy_base_dir}/configs/${each.value.source_kind}.yaml"
  path_to_function_zip            = module.psoxy_aws.path_to_deployment_jar
  function_zip_hash               = module.psoxy_aws.deployment_package_hash
  api_caller_role_arn             = module.psoxy_aws.api_caller_role_arn
  aws_assume_role_arn             = var.aws_assume_role_arn
  example_api_calls               = each.value.example_api_calls
  aws_account_id                  = var.aws_account_id
  path_to_repo_root               = var.psoxy_base_dir
  todo_step                       = module.msft_365_grants[each.key].next_todo_step
  global_parameter_arns           = module.global_secrets.secret_arns
  path_to_instance_ssm_parameters = "${var.aws_ssm_param_root_path}PSOXY_${upper(replace(each.key, "-", "_"))}_"
  ssm_kms_key_ids                 = local.ssm_key_ids
  target_host                     = each.value.target_host
  source_auth_strategy            = each.value.source_auth_strategy

  environment_variables = merge(
    var.general_environment_variables,
    try(each.value.environment_variables, {}),
    {
      IS_DEVELOPMENT_MODE  = contains(var.non_production_connectors, each.key)
      CLIENT_ID            = module.msft_connection[each.key].connector.application_id
      PSEUDONYMIZE_APP_IDS = tostring(var.pseudonymize_app_ids)
      IDENTITY_POOL_ID     = module.cognito_identity_pool[0].pool_id,
      IDENTITY_ID          = module.cognito_identity[0].identity_id[each.key]
      DEVELOPER_NAME_ID    = module.cognito_identity_pool[0].developer_provider_name
      CUSTOM_RULES_SHA     = try(var.custom_rest_rules[each.key], null) != null ? filesha1(var.custom_rest_rules[each.key]) : null
    }
  )
}

# TODO: remove in v0.5
moved {
  from = module.psoxy-msft-connector
  to   = module.psoxy_msft_connector
}

resource "aws_iam_role_policy_attachment" "cognito_lambda_policy" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  role       = module.psoxy_msft_connector[each.key].instance_role_name
  policy_arn = module.cognito_identity_pool[0].policy_arn
}

module "worklytics_psoxy_connection_msft_365" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  source = "../../modules/worklytics-psoxy-connection"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection?ref=v0.4.22"

  psoxy_host_platform_id = local.host_platform_id
  psoxy_instance_id      = each.key
  connector_id           = try(each.value.worklytics_connector_id, "")
  psoxy_endpoint_url     = module.psoxy_msft_connector[each.key].endpoint_url
  display_name           = "${each.value.display_name} via Psoxy${var.connector_display_name_suffix}"
  todo_step              = module.psoxy_msft_connector[each.key].next_todo_step

  settings_to_provide = {
    "AWS Psoxy Region"   = var.aws_region,
    "AWS Psoxy Role ARN" = module.psoxy_aws.api_caller_role_arn
  }
}

# TODO: remove in v0.5
moved {
  from = module.worklytics-psoxy-connection-msft-365
  to   = module.worklytics_psoxy_connection_msft_365
}

# END MSFT-365 CONNECTORS

# BEGIN LONG ACCESS AUTH CONNECTORS
# Create secure parameters (later filled by customer)
# Can be later passed on to a module and store in other vault if needed
locals {
  long_access_parameters = { for entry in module.worklytics_connector_specs.enabled_oauth_secrets_to_create : "${entry.connector_name}.${entry.secret_name}" => entry }
  long_access_parameters_by_connector = { for k, spec in module.worklytics_connector_specs.enabled_oauth_long_access_connectors :
    k => [for secret in spec.secured_variables : "${k}.${secret.name}"]
  }
}

resource "aws_ssm_parameter" "long-access-secrets" {
  for_each = { for entry in module.worklytics_connector_specs.enabled_oauth_secrets_to_create : "${entry.connector_name}.${entry.secret_name}" => entry }

  name        = "${var.aws_ssm_param_root_path}PSOXY_${upper(replace(each.value.connector_name, "-", "_"))}_${upper(each.value.secret_name)}"
  type        = "SecureString"
  description = "Stores the value of ${upper(each.value.secret_name)} for `psoxy-${each.value.connector_name}`"
  value       = sensitive("TODO: fill me with the proper value for ${upper(each.value.secret_name)}!! (via AWS console)")
  key_id      = coalesce(var.aws_ssm_key_id, "alias/aws/ssm")

  lifecycle {
    ignore_changes = [
      value # we expect this to be filled via Console, so don't want to overwrite it with the dummy value if changed
    ]
  }
}

module "parameter_fill_instructions" {
  for_each = local.long_access_parameters

  source = "../../modules/aws-ssm-fill-md"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-ssm-fill-md?ref=v0.4.22"

  region         = var.aws_region
  parameter_name = aws_ssm_parameter.long-access-secrets[each.key].name
}

# TODO: remove in v0.5
moved {
  from = module.parameter-fill-instructions
  to   = module.parameter_fill_instructions
}

module "source_token_external_todo" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors_todos

  source = "../../modules/source-token-external-todo"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/source-token-external-todo?ref=v0.4.22"

  source_id                         = each.key
  connector_specific_external_steps = each.value.external_token_todo
  todo_step                         = 1

  additional_steps = [for parameter_ref in local.long_access_parameters_by_connector[each.key] : module.parameter_fill_instructions[parameter_ref].todo_markdown]
}

module "aws_psoxy_long_auth_connectors" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  source = "../../modules/aws-psoxy-rest"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-rest?ref=v0.4.22"

  function_name                   = "psoxy-${each.key}"
  path_to_function_zip            = module.psoxy_aws.path_to_deployment_jar
  function_zip_hash               = module.psoxy_aws.deployment_package_hash
  path_to_config                  = null
  aws_account_id                  = var.aws_account_id
  aws_assume_role_arn             = var.aws_assume_role_arn
  api_caller_role_arn             = module.psoxy_aws.api_caller_role_arn
  source_kind                     = each.value.source_kind
  path_to_repo_root               = var.psoxy_base_dir
  example_api_calls               = each.value.example_api_calls
  reserved_concurrent_executions  = each.value.reserved_concurrent_executions
  global_parameter_arns           = module.global_secrets.secret_arns
  function_parameters             = each.value.secured_variables
  path_to_instance_ssm_parameters = "${var.aws_ssm_param_root_path}PSOXY_${upper(replace(each.key, "-", "_"))}_"
  ssm_kms_key_ids                 = local.ssm_key_ids
  target_host                     = each.value.target_host
  source_auth_strategy            = each.value.source_auth_strategy
  oauth_scopes                    = try(each.value.oauth_scopes_needed, [])

  todo_step = module.source_token_external_todo[each.key].next_todo_step

  environment_variables = merge(
    var.general_environment_variables,
    try(each.value.environment_variables, {}),
    {
      IS_DEVELOPMENT_MODE  = contains(var.non_production_connectors, each.key)
      CUSTOM_RULES_SHA     = try(var.custom_rest_rules[each.key], null) != null ? filesha1(var.custom_rest_rules[each.key]) : null
      PSEUDONYMIZE_APP_IDS = tostring(var.pseudonymize_app_ids)
    }
  )
}

# TODO: remove in v0.5
moved {
  from = module.aws-psoxy-long-auth-connectors
  to   = module.aws_psoxy_long_auth_connectors
}



module "worklytics_psoxy_connection" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  source = "../../modules/worklytics-psoxy-connection"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection?ref=v0.4.22"

  psoxy_instance_id  = each.key
  connector_id       = try(each.value.worklytics_connector_id, "")
  psoxy_endpoint_url = module.aws_psoxy_long_auth_connectors[each.key].endpoint_url
  display_name       = try(each.value.worklytics_connector_name, "${each.value.display_name} via Psoxy")
  todo_step          = module.aws_psoxy_long_auth_connectors[each.key].next_todo_step

  settings_to_provide = {
    "AWS Psoxy Region"   = var.aws_region,
    "AWS Psoxy Role ARN" = module.psoxy_aws.api_caller_role_arn
  }
}

# TODO: remove in v0.5
moved {
  from = module.worklytics-psoxy-connection
  to   = module.worklytics_psoxy_connection
}


# END LONG ACCESS AUTH CONNECTORS

module "custom_rest_rules" {
  source = "../../modules/aws-ssm-rules"

  for_each = var.custom_rest_rules

  prefix    = "${var.aws_ssm_param_root_path}PSOXY_${upper(replace(each.key, "-", "_"))}_"
  file_path = each.value
}

# BEGIN BULK CONNECTORS

module "psoxy_bulk" {
  for_each = merge(module.worklytics_connector_specs.enabled_bulk_connectors, var.custom_bulk_connectors)

  source = "../../modules/aws-psoxy-bulk"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-bulk?ref=v0.4.22"

  aws_account_id                   = var.aws_account_id
  aws_assume_role_arn              = var.aws_assume_role_arn
  provision_iam_policy_for_testing = var.provision_testing_infra
  aws_role_to_assume_when_testing  = var.provision_testing_infra ? module.psoxy_aws.api_caller_role_arn : null
  instance_id                      = each.key
  source_kind                      = each.value.source_kind
  aws_region                       = var.aws_region
  path_to_function_zip             = module.psoxy_aws.path_to_deployment_jar
  function_zip_hash                = module.psoxy_aws.deployment_package_hash
  psoxy_base_dir                   = var.psoxy_base_dir
  rules                            = each.value.rules
  global_parameter_arns            = module.global_secrets.secret_arns
  path_to_instance_ssm_parameters  = "${var.aws_ssm_param_root_path}PSOXY_${upper(replace(each.key, "-", "_"))}_"
  ssm_kms_key_ids                  = local.ssm_key_ids
  sanitized_accessor_role_names    = [module.psoxy_aws.api_caller_role_name]
  memory_size_mb                   = 1024
  sanitized_expiration_days        = var.bulk_sanitized_expiration_days
  input_expiration_days            = var.bulk_input_expiration_days
  example_file                     = each.value.example_file

  environment_variables = merge(
    var.general_environment_variables,
    try(each.value.environment_variables, {}),
    {
      IS_DEVELOPMENT_MODE = contains(var.non_production_connectors, each.key)
    },
  )
}

# TODO: remove in v0.5
moved {
  from = module.psoxy-bulk
  to   = module.psoxy_bulk
}


module "psoxy_bulk_to_worklytics" {
  for_each = merge(module.worklytics_connector_specs.enabled_bulk_connectors,
  var.custom_bulk_connectors)

  source = "../../modules/worklytics-psoxy-connection-generic"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection-generic?ref=v0.4.22"

  psoxy_host_platform_id = local.host_platform_id
  psoxy_instance_id      = each.key
  connector_id           = try(each.value.worklytics_connector_id, "")
  display_name           = try(each.value.worklytics_connector_name, "${each.value.source_kind} via Psoxy")
  todo_step              = module.psoxy_bulk[each.key].next_todo_step

  settings_to_provide = merge({
    "AWS Psoxy Region"   = var.aws_region,
    "AWS Psoxy Role ARN" = module.psoxy_aws.api_caller_role_arn
    "Bucket Name"        = module.psoxy_bulk[each.key].sanitized_bucket
  }, try(each.value.settings_to_provide, {}))
}

# TODO: remove in v0.5
moved {
  from = module.psoxy-bulk-to-worklytics
  to   = module.psoxy_bulk_to_worklytics
}

# BEGIN lookup tables
module "lookup_output" {
  for_each = var.lookup_table_builders

  source = "../../modules/aws-psoxy-output-bucket"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-output-bucket?ref=v0.4.22"

  instance_id                   = each.key
  iam_role_for_lambda_name      = module.psoxy_bulk[each.value.input_connector_id].instance_role_name
  sanitized_accessor_role_names = each.value.sanitized_accessor_role_names
}

locals {
  inputs_to_build_lookups_for = toset(distinct([for k, v in var.lookup_table_builders : v.input_connector_id]))
}

resource "aws_ssm_parameter" "additional_transforms" {
  for_each = local.inputs_to_build_lookups_for

  name = "${var.aws_ssm_param_root_path}PSOXY_${upper(replace(each.key, "-", "_"))}_ADDITIONAL_TRANSFORMS"
  type = "String"
  value = yamlencode([for k, v in var.lookup_table_builders : {
    destinationBucketName : module.lookup_output[k].output_bucket
    rules : v.rules
  } if v.input_connector_id == each.key])
}

# END lookup tables


locals {
  all_instances = merge(
    { for instance in module.psoxy_google_workspace_connector : instance.instance_id => instance },
    { for instance in module.psoxy_bulk : instance.instance_id => instance },
    { for instance in module.aws_psoxy_long_auth_connectors : instance.instance_id => instance }
  )
}
