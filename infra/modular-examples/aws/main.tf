terraform {
  required_providers {
    # for the infra that will host Psoxy instances
    aws = {
      source  = "hashicorp/aws"
      version = ">= 4.12"
    }
  }
}

# NOTE: region used to be passed in as a variable; put it MUST match the region in which the lambda
# is provisioned, and that's implicit in the provider - so we should just infer from the provider
data "aws_region" "current" {}

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
  base_config_path     = "${var.psoxy_base_dir}/configs/"
  host_platform_id     = "AWS"
  ssm_key_ids          = var.aws_ssm_key_id == null ? {} : { 0 : var.aws_ssm_key_id }
  deployment_id        = length(var.environment_name) > 0 ? replace(lower(var.environment_name), " ", "-") : random_string.deployment_id.result
  proxy_brand          = "psoxy"
  function_name_prefix = "${local.proxy_brand}-"
}

module "worklytics_connector_specs" {
  source = "../../modules/worklytics-connector-specs"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-connector-specs?ref=rc-v0.5.0

  enabled_connectors             = var.enabled_connectors
  google_workspace_example_user  = var.google_workspace_example_user
  google_workspace_example_admin = try(coalesce(var.google_workspace_example_admin, var.google_workspace_example_user), null)
  salesforce_domain              = var.salesforce_domain
  msft_tenant_id                 = var.msft_tenant_id
  jira_server_url                = var.jira_server_url
  jira_cloud_id                  = var.jira_cloud_id
  example_jira_issue_id          = var.example_jira_issue_id
  github_api_host                = var.github_api_host
  github_enterprise_server_host  = var.github_enterprise_server_host
  github_installation_id         = var.github_installation_id
  github_organization            = var.github_organization
  github_example_repository      = var.github_example_repository
}

module "psoxy_aws" {
  source = "../../modules/aws"
<<<<<<< HEAD
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws?ref=v0.4.62
=======
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws?ref=rc-v0.5.0
>>>>>>> 533c58d5 (update release refs to rc-v0.5.0)

  aws_account_id                 = var.aws_account_id
  region                         = data.aws_region.current.id
  psoxy_base_dir                 = var.psoxy_base_dir
  deployment_bundle              = var.deployment_bundle
  caller_aws_arns                = var.caller_aws_arns
  caller_gcp_service_account_ids = var.caller_gcp_service_account_ids
  force_bundle                   = var.force_bundle
  install_test_tool              = var.install_test_tool
  deployment_id                  = length(var.environment_name) > 0 ? var.environment_name : "Psoxy"
  api_function_name_prefix       = local.function_name_prefix
}

# TODO: remove in v0.5
moved {
  from = module.psoxy-aws
  to   = module.psoxy_aws
}

# secrets shared across all instances
module "global_secrets" {
  source = "../../modules/aws-ssm-secrets"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-ssm-secrets?ref=rc-v0.5.0

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
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/google-workspace-dwd-connection?ref=rc-v0.5.0

  project_id                   = var.gcp_project_id
  connector_service_account_id = "${local.function_name_prefix}${local.deployment_id_sa_id_part}${each.key}"
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
<<<<<<< HEAD
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-sa-auth-key?ref=v0.4.62"
=======
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-sa-auth-key?ref=rc-v0.5.0"
>>>>>>> 533c58d5 (update release refs to rc-v0.5.0)

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
<<<<<<< HEAD
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-ssm-secrets?ref=v0.4.62"
=======
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-ssm-secrets?ref=rc-v0.5.0"
>>>>>>> 533c58d5 (update release refs to rc-v0.5.0)
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
<<<<<<< HEAD
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-rest?ref=v0.4.62"
=======
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-rest?ref=rc-v0.5.0"
>>>>>>> 533c58d5 (update release refs to rc-v0.5.0)

  environment_name                      = var.environment_name
  instance_id                           = each.key
  source_kind                           = each.value.source_kind
  path_to_function_zip                  = module.psoxy_aws.path_to_deployment_jar
  function_zip_hash                     = module.psoxy_aws.deployment_package_hash
  path_to_config                        = null
  api_caller_role_arn                   = module.psoxy_aws.api_caller_role_arn
  aws_assume_role_arn                   = var.aws_assume_role_arn
  aws_account_id                        = var.aws_account_id
  region                                = data.aws_region.current.id
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
<<<<<<< HEAD
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection?ref=v0.4.62"
=======
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection?ref=rc-v0.5.0"
>>>>>>> 533c58d5 (update release refs to rc-v0.5.0)

  psoxy_instance_id      = each.key
  psoxy_host_platform_id = local.host_platform_id
  connector_id           = try(each.value.worklytics_connector_id, "")
  psoxy_endpoint_url     = module.psoxy_google_workspace_connector[each.key].endpoint_url
  display_name           = "${each.value.display_name} via Psoxy${var.connector_display_name_suffix}"
  todo_step              = module.psoxy_google_workspace_connector[each.key].next_todo_step

  settings_to_provide = {
    "AWS Psoxy Region"   = data.aws_region.current.id,
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
<<<<<<< HEAD
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-cognito-pool?ref=v0.4.62"
=======
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-cognito-pool?ref=rc-v0.5.0"
>>>>>>> 533c58d5 (update release refs to rc-v0.5.0)

  developer_provider_name = "azure-access"
  name                    = "azure-ad-federation"
}

module "cognito_identity" {
  count = local.msft_365_enabled ? 1 : 0 # only provision identity pool if MSFT-365 connectors are enabled

  source = "../../modules/aws-cognito-identity-cli"
<<<<<<< HEAD
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-cognito-identity-cli?ref=v0.4.62"
=======
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-cognito-identity-cli?ref=rc-v0.5.0"
>>>>>>> 533c58d5 (update release refs to rc-v0.5.0)

  identity_pool_id = module.cognito_identity_pool[0].pool_id
  aws_region       = data.aws_region.current.id
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
<<<<<<< HEAD
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-connection?ref=v0.4.62"
=======
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-connection?ref=rc-v0.5.0"
>>>>>>> 533c58d5 (update release refs to rc-v0.5.0)

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
<<<<<<< HEAD
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-federated-credentials?ref=v0.4.62"
=======
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-federated-credentials?ref=rc-v0.5.0"
>>>>>>> 533c58d5 (update release refs to rc-v0.5.0)

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
<<<<<<< HEAD
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-grant-all-users?ref=v0.4.62"
=======
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-grant-all-users?ref=rc-v0.5.0"
>>>>>>> 533c58d5 (update release refs to rc-v0.5.0)

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
<<<<<<< HEAD
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-rest?ref=v0.4.62"
=======
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-rest?ref=rc-v0.5.0"
>>>>>>> 533c58d5 (update release refs to rc-v0.5.0)

  environment_name                = var.environment_name
  instance_id                     = each.key
  source_kind                     = each.value.source_kind
  path_to_config                  = "${var.psoxy_base_dir}configs/${each.value.source_kind}.yaml"
  path_to_function_zip            = module.psoxy_aws.path_to_deployment_jar
  function_zip_hash               = module.psoxy_aws.deployment_package_hash
  api_caller_role_arn             = module.psoxy_aws.api_caller_role_arn
  aws_assume_role_arn             = var.aws_assume_role_arn
  example_api_calls               = each.value.example_api_calls
  aws_account_id                  = var.aws_account_id
  region                          = data.aws_region.current.id
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
      PSEUDONYMIZE_APP_IDS = tostring(var.pseudonymize_app_ids)
      CUSTOM_RULES_SHA     = try(var.custom_rest_rules[each.key], null) != null ? filesha1(var.custom_rest_rules[each.key]) : null
      IS_DEVELOPMENT_MODE  = contains(var.non_production_connectors, each.key)

      CLIENT_ID         = module.msft_connection[each.key].connector.application_id
      IDENTITY_POOL_ID  = module.cognito_identity_pool[0].pool_id,
      IDENTITY_ID       = module.cognito_identity[0].identity_id[each.key]
      DEVELOPER_NAME_ID = module.cognito_identity_pool[0].developer_provider_name
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
<<<<<<< HEAD
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection?ref=v0.4.62"
=======
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection?ref=rc-v0.5.0"
>>>>>>> 533c58d5 (update release refs to rc-v0.5.0)

  psoxy_host_platform_id = local.host_platform_id
  psoxy_instance_id      = each.key
  connector_id           = try(each.value.worklytics_connector_id, "")
  psoxy_endpoint_url     = module.psoxy_msft_connector[each.key].endpoint_url
  display_name           = "${each.value.display_name} via Psoxy${var.connector_display_name_suffix}"
  todo_step              = module.psoxy_msft_connector[each.key].next_todo_step

  settings_to_provide = {
    "AWS Psoxy Region"   = data.aws_region.current.id,
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
<<<<<<< HEAD
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-ssm-fill-md?ref=v0.4.62"
=======
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-ssm-fill-md?ref=rc-v0.5.0"
>>>>>>> 533c58d5 (update release refs to rc-v0.5.0)

  region         = data.aws_region.current.id
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
<<<<<<< HEAD
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/source-token-external-todo?ref=v0.4.62"
=======
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/source-token-external-todo?ref=rc-v0.5.0"
>>>>>>> 533c58d5 (update release refs to rc-v0.5.0)

  source_id                         = each.key
  connector_specific_external_steps = each.value.external_token_todo
  todo_step                         = 1

  additional_steps = [for parameter_ref in local.long_access_parameters_by_connector[each.key] : module.parameter_fill_instructions[parameter_ref].todo_markdown]
}

module "aws_psoxy_long_auth_connectors" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  source = "../../modules/aws-psoxy-rest"
<<<<<<< HEAD
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-rest?ref=v0.4.62"
=======
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-rest?ref=rc-v0.5.0"
>>>>>>> 533c58d5 (update release refs to rc-v0.5.0)

  environment_name                = var.environment_name
  instance_id                     = each.key
  path_to_function_zip            = module.psoxy_aws.path_to_deployment_jar
  function_zip_hash               = module.psoxy_aws.deployment_package_hash
  path_to_config                  = null
  aws_account_id                  = var.aws_account_id
  region                          = data.aws_region.current.id
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
<<<<<<< HEAD
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection?ref=v0.4.62"
=======
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection?ref=rc-v0.5.0"
>>>>>>> 533c58d5 (update release refs to rc-v0.5.0)

  psoxy_instance_id  = each.key
  connector_id       = try(each.value.worklytics_connector_id, "")
  psoxy_endpoint_url = module.aws_psoxy_long_auth_connectors[each.key].endpoint_url
  display_name       = try(each.value.worklytics_connector_name, "${each.value.display_name} via Psoxy")
  todo_step          = module.aws_psoxy_long_auth_connectors[each.key].next_todo_step
  worklytics_host    = var.worklytics_host

  settings_to_provide = {
    "AWS Psoxy Region"   = data.aws_region.current.id,
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
<<<<<<< HEAD
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-bulk?ref=v0.4.62"
=======
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-bulk?ref=rc-v0.5.0"
>>>>>>> 533c58d5 (update release refs to rc-v0.5.0)

  environment_name                 = var.environment_name
  instance_id                      = each.key
  aws_account_id                   = var.aws_account_id
  aws_assume_role_arn              = var.aws_assume_role_arn
  provision_iam_policy_for_testing = var.provision_testing_infra
  aws_role_to_assume_when_testing  = var.provision_testing_infra ? module.psoxy_aws.api_caller_role_arn : null
  source_kind                      = each.value.source_kind
  aws_region                       = data.aws_region.current.id
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
<<<<<<< HEAD
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection-generic?ref=v0.4.62"
=======
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection-generic?ref=rc-v0.5.0"
>>>>>>> 533c58d5 (update release refs to rc-v0.5.0)

  psoxy_host_platform_id = local.host_platform_id
  psoxy_instance_id      = each.key
  connector_id           = try(each.value.worklytics_connector_id, "")
  display_name           = try(each.value.worklytics_connector_name, "${each.value.source_kind} via Psoxy")
  todo_step              = module.psoxy_bulk[each.key].next_todo_step
  worklytics_host        = var.worklytics_host

  settings_to_provide = merge({
    "AWS Psoxy Region"   = data.aws_region.current.id,
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
<<<<<<< HEAD
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-output-bucket?ref=v0.4.62"
=======
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-output-bucket?ref=rc-v0.5.0"
>>>>>>> 533c58d5 (update release refs to rc-v0.5.0)

  environment_name              = var.environment_name
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
