terraform {
  required_providers {
    # for the infra that will host Psoxy instances
    aws = {
      source  = "hashicorp/aws"
      version = ">= 4.12"
    }

    # for API connections to Microsoft 365
    azuread = {
      version = ">= 2.3, < 3.0"
    }
  }
}

locals {
  base_config_path = "${var.psoxy_base_dir}configs/"
  host_platform_id = "AWS"
  ssm_key_ids      = var.aws_ssm_key_id == null ? {} : { 0 : var.aws_ssm_key_id }
}

data "azuread_client_config" "current" {}

module "worklytics_connector_specs" {
  source = "../../modules/worklytics-connector-specs"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-connector-specs?ref=v0.4.11"

  enabled_connectors = var.enabled_connectors
  msft_tenant_id     = var.msft_tenant_id
  # this IS the correct ID for the user terraform is running as, which we assume is a user who's OK
  # to use the subject of examples. You can change it to any string you want.
  example_msft_user_guid = data.azuread_client_config.current.object_id
}

module "psoxy-aws" {
  source = "../../modules/aws"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws?ref=v0.4.11"

  aws_account_id                 = var.aws_account_id
  psoxy_base_dir                 = var.psoxy_base_dir
  caller_aws_arns                = var.caller_aws_arns
  caller_gcp_service_account_ids = var.caller_gcp_service_account_ids
  force_bundle                   = var.force_bundle
}

module "global_secrets" {
  source = "../../modules/aws-ssm-secrets"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-ssm-secrets?ref=v0.4.11"

  path       = var.aws_ssm_param_root_path
  kms_key_id = var.aws_ssm_key_id
  secrets    = module.psoxy-aws.secrets
}

moved {
  from = module.psoxy-aws.aws_ssm_parameter.salt
  to   = module.global_secrets.aws_ssm_parameter.secret["PSOXY_SALT"]
}

moved {
  from = module.psoxy-aws.aws_ssm_parameter.encryption_key
  to   = module.global_secrets.aws_ssm_parameter.secret["PSOXY_ENCRYPTION_KEY"]
}

module "cognito-identity-pool" {
  source = "../../modules/aws-cognito-pool"

  developer_provider_name = "azure-access"
  name                    = "azure-ad-federation"
}

data "azuread_users" "owners" {
  user_principal_names = var.msft_owners_email
}

module "msft-connection" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  source = "../../modules/azuread-connection"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-connection?ref=v0.4.11"

  display_name                      = "Psoxy Connector - ${each.value.display_name}${var.connector_display_name_suffix}"
  tenant_id                         = var.msft_tenant_id
  required_app_roles                = each.value.required_app_roles
  required_oauth2_permission_scopes = each.value.required_oauth2_permission_scopes
  owners                            = toset(concat(data.azuread_users.owners.object_ids, [data.azuread_client_config.current.object_id]))
}

module "cognito-identity" {
  source = "../../modules/aws-cognito-identity-cli"

  identity_pool_id = module.cognito-identity-pool.pool_id
  aws_region       = var.aws_region
  login_ids        = { for k in keys(module.msft-connection) : k => "${module.cognito-identity-pool.developer_provider_name}=${module.msft-connection[k].connector.application_id}" }
  aws_role         = var.aws_assume_role_arn
}

module "msft-connection-auth-federation" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  source = "../../modules/azuread-federated-credentials"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-federated-credentials?ref=v0.4.11"

  application_object_id = module.msft-connection[each.key].connector.id
  display_name          = "AccessFromAWS"
  description           = "AWS federation to be used for psoxy Connector - ${each.value.display_name}${var.connector_display_name_suffix}"
  issuer                = "https://cognito-identity.amazonaws.com"
  audience              = module.cognito-identity-pool.pool_id
  subject               = module.cognito-identity.identity_id[each.key]
}

# grant required permissions to connectors via Azure AD
# (requires terraform configuration being applied by an Azure User with privileges to do this; it
#  usually requires a 'Global Administrator' for your tenant)
module "msft_365_grants" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  source = "../../modules/azuread-grant-all-users"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-grant-all-users?ref=v0.4.11"

  psoxy_instance_id        = each.key
  application_id           = module.msft-connection[each.key].connector.application_id
  oauth2_permission_scopes = each.value.required_oauth2_permission_scopes
  app_roles                = each.value.required_app_roles
  application_name         = each.key
  todo_step                = 1
}

module "psoxy-msft-connector" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  source = "../../modules/aws-psoxy-rest"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-rest?ref=v0.4.11"

  function_name                   = "psoxy-${each.key}"
  source_kind                     = each.value.source_kind
  path_to_config                  = "${var.psoxy_base_dir}/configs/${each.value.source_kind}.yaml"
  path_to_function_zip            = module.psoxy-aws.path_to_deployment_jar
  function_zip_hash               = module.psoxy-aws.deployment_package_hash
  api_caller_role_arn             = module.psoxy-aws.api_caller_role_arn
  aws_assume_role_arn             = var.aws_assume_role_arn
  example_api_calls               = each.value.example_api_calls
  aws_account_id                  = var.aws_account_id
  path_to_repo_root               = var.psoxy_base_dir
  todo_step                       = module.msft_365_grants[each.key].next_todo_step
  global_parameter_arns           = module.global_secrets.secret_arns
  path_to_instance_ssm_parameters = "${var.aws_ssm_param_root_path}PSOXY_${upper(replace(each.key, "-", "_"))}_"
  ssm_kms_key_ids                 = local.ssm_key_ids

  environment_variables = merge(
    var.general_environment_variables,
    try(each.value.environment_variables, {}),
    {
      IS_DEVELOPMENT_MODE  = contains(var.non_production_connectors, each.key)
      CLIENT_ID            = module.msft-connection[each.key].connector.application_id
      REFRESH_ENDPOINT     = module.worklytics_connector_specs.msft_token_refresh_endpoint
      PSEUDONYMIZE_APP_IDS = tostring(var.pseudonymize_app_ids)
      IDENTITY_POOL_ID     = module.cognito-identity-pool.pool_id,
      IDENTITY_ID          = module.cognito-identity.identity_id[each.key]
      DEVELOPER_NAME_ID    = module.cognito-identity-pool.developer_provider_name
    }
  )
}

resource "aws_iam_role_policy_attachment" "cognito_lambda_policy" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  role       = module.psoxy-msft-connector[each.key].instance_role_name
  policy_arn = module.cognito-identity-pool.policy_arn
}

module "worklytics-psoxy-connection-msft-365" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  source = "../../modules/worklytics-psoxy-connection"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection?ref=v0.4.11"

  psoxy_host_platform_id = local.host_platform_id
  psoxy_instance_id      = each.key
  connector_id           = try(each.value.worklytics_connector_id, "")
  psoxy_endpoint_url     = module.psoxy-msft-connector[each.key].endpoint_url
  display_name           = "${each.value.display_name} via Psoxy${var.connector_display_name_suffix}"
  todo_step              = module.psoxy-msft-connector[each.key].next_todo_step

  settings_to_provide = {
    "AWS Psoxy Region"   = var.aws_region,
    "AWS Psoxy Role ARN" = module.psoxy-aws.api_caller_role_arn
  }
}

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

module "parameter-fill-instructions" {
  for_each = local.long_access_parameters

  source = "../../modules/aws-ssm-fill-md"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-ssm-fill-md?ref=v0.4.11"

  region         = var.aws_region
  parameter_name = aws_ssm_parameter.long-access-secrets[each.key].name
}

module "source_token_external_todo" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors_todos

  source = "../../modules/source-token-external-todo"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/source-token-external-todo?ref=v0.4.11"

  source_id                         = each.key
  connector_specific_external_steps = each.value.external_token_todo
  todo_step                         = 1

  additional_steps = [for parameter_ref in local.long_access_parameters_by_connector[each.key] : module.parameter-fill-instructions[parameter_ref].todo_markdown]
}

module "aws-psoxy-long-auth-connectors" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  source = "../../modules/aws-psoxy-rest"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-rest?ref=v0.4.11"

  function_name                         = "psoxy-${each.key}"
  path_to_function_zip                  = module.psoxy-aws.path_to_deployment_jar
  function_zip_hash                     = module.psoxy-aws.deployment_package_hash
  path_to_config                        = "${local.base_config_path}${each.value.source_kind}.yaml"
  aws_assume_role_arn                   = var.aws_assume_role_arn
  aws_account_id                        = var.aws_account_id
  api_caller_role_arn                   = module.psoxy-aws.api_caller_role_arn
  source_kind                           = each.value.source_kind
  path_to_repo_root                     = var.psoxy_base_dir
  example_api_calls                     = each.value.example_api_calls
  example_api_calls_user_to_impersonate = each.value.example_api_calls_user_to_impersonate
  todo_step                             = module.source_token_external_todo[each.key].next_todo_step
  reserved_concurrent_executions        = each.value.reserved_concurrent_executions
  global_parameter_arns                 = module.global_secrets.secret_arns
  path_to_instance_ssm_parameters       = "${var.aws_ssm_param_root_path}PSOXY_${upper(replace(each.key, "-", "_"))}_"
  function_parameters                   = each.value.secured_variables
  ssm_kms_key_ids                       = local.ssm_key_ids

  environment_variables = merge(
    var.general_environment_variables,
    try(each.value.environment_variables, {}),
    {
      PSEUDONYMIZE_APP_IDS = tostring(var.pseudonymize_app_ids)
      IS_DEVELOPMENT_MODE  = contains(var.non_production_connectors, each.key)
    }
  )
}

module "worklytics-psoxy-connection-oauth-long-access" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  source = "../../modules/worklytics-psoxy-connection"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection?ref=v0.4.11"

  psoxy_host_platform_id = local.host_platform_id
  psoxy_instance_id      = each.key
  connector_id           = try(each.value.worklytics_connector_id, "")
  psoxy_endpoint_url     = module.aws-psoxy-long-auth-connectors[each.key].endpoint_url
  display_name           = "${each.value.display_name} via Psoxy${var.connector_display_name_suffix}"
  todo_step              = module.aws-psoxy-long-auth-connectors[each.key].next_todo_step

  settings_to_provide = {
    "AWS Psoxy Region"   = var.aws_region,
    "AWS Psoxy Role ARN" = module.psoxy-aws.api_caller_role_arn
  }
}

# END LONG ACCESS AUTH CONNECTORS

module "psoxy-bulk" {
  for_each = merge(module.worklytics_connector_specs.enabled_bulk_connectors,
  var.custom_bulk_connectors)

  source = "../../modules/aws-psoxy-bulk"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-bulk?ref=v0.4.11"

  aws_account_id                  = var.aws_account_id
  aws_assume_role_arn             = var.aws_assume_role_arn
  instance_id                     = each.key
  source_kind                     = each.value.source_kind
  aws_region                      = var.aws_region
  path_to_function_zip            = module.psoxy-aws.path_to_deployment_jar
  function_zip_hash               = module.psoxy-aws.deployment_package_hash
  api_caller_role_arn             = module.psoxy-aws.api_caller_role_arn
  api_caller_role_name            = module.psoxy-aws.api_caller_role_name
  memory_size_mb                  = 1024
  psoxy_base_dir                  = var.psoxy_base_dir
  rules                           = each.value.rules
  global_parameter_arns           = module.global_secrets.secret_arns
  path_to_instance_ssm_parameters = "${var.aws_ssm_param_root_path}PSOXY_${upper(replace(each.key, "-", "_"))}_"
  ssm_kms_key_ids                 = local.ssm_key_ids

  sanitized_accessor_role_names = [
    module.psoxy-aws.api_caller_role_name
  ]

  environment_variables = merge(
    var.general_environment_variables,
    try(each.value.environment_variables, {}),
    {
      IS_DEVELOPMENT_MODE = contains(var.non_production_connectors, each.key)
    }
  )
}

module "psoxy-bulk-to-worklytics" {
  for_each = merge(module.worklytics_connector_specs.enabled_bulk_connectors,
  var.custom_bulk_connectors)

  source = "../../modules/worklytics-psoxy-connection-generic"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection-generic?ref=v0.4.11"

  psoxy_host_platform_id = local.host_platform_id
  psoxy_instance_id      = each.key
  connector_id           = try(each.value.worklytics_connector_id, "")
  display_name           = try(each.value.worklytics_connector_name, "${each.value.source_kind} via Psoxy")
  todo_step              = module.psoxy-bulk[each.key].next_todo_step

  settings_to_provide = merge({
    "AWS Psoxy Region"   = var.aws_region,
    "AWS Psoxy Role ARN" = module.psoxy-aws.api_caller_role_arn
    "Bucket Name"        = module.psoxy-bulk[each.key].sanitized_bucket
  }, try(each.value.settings_to_provide, {}))
}

module "psoxy_lookup_tables_builders" {
  for_each = var.lookup_table_builders

  source = "../../modules/aws-psoxy-bulk-existing"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-bulk-existing?ref=v0.4.11"

  input_bucket                    = module.psoxy-bulk[each.value.input_connector_id].input_bucket
  aws_account_id                  = var.aws_account_id
  instance_id                     = each.key
  aws_region                      = var.aws_region
  path_to_function_zip            = module.psoxy-aws.path_to_deployment_jar
  function_zip_hash               = module.psoxy-aws.deployment_package_hash
  psoxy_base_dir                  = var.psoxy_base_dir
  rules                           = each.value.rules
  global_parameter_arns           = module.global_secrets.secret_arns
  sanitized_accessor_role_names   = each.value.sanitized_accessor_role_names
  path_to_instance_ssm_parameters = "${var.aws_ssm_param_root_path}PSOXY_${upper(replace(each.key, "-", "_"))}_"
  ssm_kms_key_ids                 = local.ssm_key_ids

  environment_variables = merge(
    var.general_environment_variables,
    try(each.value.environment_variables, {}),
    {
      IS_DEVELOPMENT_MODE = contains(var.non_production_connectors, each.key)
    }
  )
}

output "lookup_tables" {
  value = { for k, v in var.lookup_table_builders : k => module.psoxy_lookup_tables_builders[k].output_bucket }
}

locals {
  all_instances = merge(
    { for instance in module.psoxy-msft-connector : instance.instance_id => instance },
    { for instance in module.psoxy-bulk : instance.instance_id => instance },
    { for instance in module.aws-psoxy-long-auth-connectors : instance.instance_id => instance },
    { for instance in module.psoxy_lookup_tables_builders : instance.instance_id => instance }

  )
}

output "instances" {
  value = local.all_instances
}

output "cognito_identities" {
  value = module.cognito-identity
}

output "cognito_identity_pool_id" {
  value = module.cognito-identity-pool.pool_id
}

//q: better to invert control, let calling modules write the files??
output "todos_1" {
  description = "List of TODO steps to complete 1st."
  value       = concat(
    values(module.msft_365_grants)[*].todo,
    values(module.source_token_external_todo)[*].todo,
  )
}

output "todos_2" {
  description = "List of todo steps to complete 2nd."
  value       = concat(
    values(module.psoxy-msft-connector)[*].todo,
    values(module.aws-psoxy-long-auth-connectors)[*].todo,
  )
}

output "todos_3" {
  description = "List of todo steps to complete 3rd."
  value       = concat(
    values(module.worklytics-psoxy-connection-msft-365)[*].todo,
    values(module.psoxy-bulk-to-worklytics)[*].todo,
    values(module.worklytics-psoxy-connection-oauth-long-access)[*].todo
  )
}

output "caller_role_arn" {
  value = module.psoxy-aws.api_caller_role_arn
}


