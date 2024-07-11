terraform {
  required_providers {
    # for the infra that will host Psoxy instances
    aws = {
      source  = "hashicorp/aws"
      version = ">= 4.12"
    }

    # for the API connections to Google Workspace
    google = {
      version = ">= 3.74, <= 5.0"
    }
  }
}

locals {
  base_config_path     = "${var.psoxy_base_dir}/configs/"
  host_platform_id     = "AWS"
  ssm_key_ids          = var.aws_ssm_key_id == null ? {} : { 0 : var.aws_ssm_key_id }
  function_name_prefix = "psoxy-"
}

module "worklytics_connector_specs" {
  source = "../../modules/worklytics-connector-specs"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-connector-specs?ref=v0.4.57

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

module "psoxy-aws" {
  source = "../../modules/aws"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws?ref=v0.4.57

  aws_account_id                 = var.aws_account_id
  region                         = var.aws_region
  psoxy_base_dir                 = var.psoxy_base_dir
  force_bundle                   = var.force_bundle
  caller_aws_arns                = var.caller_aws_arns
  caller_gcp_service_account_ids = var.caller_gcp_service_account_ids
  api_function_name_prefix       = local.function_name_prefix
}

# secrets shared across all instances
module "global_secrets" {
  source = "../../modules/aws-ssm-secrets"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-ssm-secrets?ref=v0.4.57

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

module "env_id_gcp_sa" {
  source = "../../modules/env-id"

  environment_name = var.environment_name
  max_length       = 20
}

module "google-workspace-connection" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/google-workspace-dwd-connection"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/google-workspace-dwd-connection?ref=v0.4.57

  project_id                   = var.gcp_project_id
  connector_service_account_id = "${module.env_id_gcp_sa.id}-${each.key}"
  display_name                 = "Psoxy Connector - ${each.value.display_name}${var.connector_display_name_suffix}"
  apis_consumed                = each.value.apis_consumed
  oauth_scopes_needed          = each.value.oauth_scopes_needed
  todo_step                    = 1

  depends_on = [
    module.psoxy-aws
  ]
}

module "google-workspace-connection-auth" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/gcp-sa-auth-key"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-sa-auth-key?ref=v0.4.57"

  service_account_id = module.google-workspace-connection[each.key].service_account_id
}

module "sa-key-secrets" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/aws-ssm-secrets"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-ssm-secrets?ref=v0.4.57"
  # other possibly implementations:
  # source = "../hashicorp-vault-secrets"

  path       = var.aws_ssm_param_root_path
  kms_key_id = var.aws_ssm_key_id

  secrets = {
    "PSOXY_${replace(upper(each.key), "-", "_")}_SERVICE_ACCOUNT_KEY" : {
      value       = module.google-workspace-connection-auth[each.key].key_value
      description = "GCP service account key for ${each.key} connector"
    }
  }
}

module "psoxy-google-workspace-connector" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/aws-psoxy-rest"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-rest?ref=v0.4.57"

  environment_name                      = var.environment_name
  instance_id                           = each.key
  source_kind                           = each.key
  path_to_function_zip                  = module.psoxy-aws.path_to_deployment_jar
  function_zip_hash                     = module.psoxy-aws.deployment_package_hash
  path_to_config                        = null
  api_caller_role_arn                   = module.psoxy-aws.api_caller_role_arn
  aws_assume_role_arn                   = var.aws_assume_role_arn
  aws_account_id                        = var.aws_account_id
  region                                = var.aws_region
  path_to_repo_root                     = var.psoxy_base_dir
  example_api_calls                     = each.value.example_api_calls
  example_api_calls_user_to_impersonate = each.value.example_api_calls_user_to_impersonate
  global_parameter_arns                 = module.global_secrets.secret_arns
  path_to_instance_ssm_parameters       = "${var.aws_ssm_param_root_path}PSOXY_${upper(replace(each.key, "-", "_"))}_"
  ssm_kms_key_ids                       = local.ssm_key_ids
  target_host                           = each.value.target_host
  source_auth_strategy                  = each.value.source_auth_strategy
  oauth_scopes                          = try(each.value.oauth_scopes_needed, [])
  log_retention_days                    = var.log_retention_days

  todo_step = module.google-workspace-connection[each.key].next_todo_step

  environment_variables = merge(
    var.general_environment_variables,
    try(each.value.environment_variables, {}),
    {
      BUNDLE_FILENAME     = module.psoxy-aws.filename
      IS_DEVELOPMENT_MODE = contains(var.non_production_connectors, each.key)
      # trickery to force lambda restart so new rules seen
      CUSTOM_RULES_SHA = try(var.custom_rest_rules[each.key], null) != null ? filesha1(var.custom_rest_rules[each.key]) : null
    }
  )
}


module "worklytics-psoxy-connection-google-workspace" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/worklytics-psoxy-connection"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection?ref=v0.4.57"

  psoxy_instance_id      = each.key
  psoxy_host_platform_id = local.host_platform_id
  connector_id           = try(each.value.worklytics_connector_id, "")
  psoxy_endpoint_url     = module.psoxy-google-workspace-connector[each.key].endpoint_url
  display_name           = "${each.value.display_name} via Psoxy${var.connector_display_name_suffix}"
  todo_step              = module.psoxy-google-workspace-connector[each.key].next_todo_step

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
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-ssm-fill-md?ref=v0.4.57"

  region         = var.aws_region
  parameter_name = aws_ssm_parameter.long-access-secrets[each.key].name
}

module "source_token_external_todo" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors_todos

  source = "../../modules/source-token-external-todo"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/source-token-external-todo?ref=v0.4.57"

  source_id                         = each.key
  connector_specific_external_steps = each.value.external_token_todo
  todo_step                         = 1

  additional_steps = [for parameter_ref in local.long_access_parameters_by_connector[each.key] : module.parameter-fill-instructions[parameter_ref].todo_markdown]
}

module "aws-psoxy-long-auth-connectors" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  source = "../../modules/aws-psoxy-rest"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-rest?ref=v0.4.57"

  environment_name                = var.environment_name
  instance_id                     = each.key
  path_to_function_zip            = module.psoxy-aws.path_to_deployment_jar
  function_zip_hash               = module.psoxy-aws.deployment_package_hash
  path_to_config                  = null
  aws_assume_role_arn             = var.aws_assume_role_arn
  aws_account_id                  = var.aws_account_id
  region                          = var.aws_region
  api_caller_role_arn             = module.psoxy-aws.api_caller_role_arn
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
  log_retention_days              = var.log_retention_days


  todo_step = module.source_token_external_todo[each.key].next_todo_step

  environment_variables = merge(
    var.general_environment_variables,
    try(each.value.environment_variables, {}),
    {
      BUNDLE_FILENAME     = module.psoxy-aws.filename
      IS_DEVELOPMENT_MODE = contains(var.non_production_connectors, each.key)
      # trickery to force lambda restart so new rules seen
      CUSTOM_RULES_SHA     = try(var.custom_rest_rules[each.key], null) != null ? filesha1(var.custom_rest_rules[each.key]) : null
      PSEUDONYMIZE_APP_IDS = tostring(var.pseudonymize_app_ids)
    }
  )
}


module "worklytics-psoxy-connection" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  source = "../../modules/worklytics-psoxy-connection"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection?ref=v0.4.57"

  psoxy_instance_id  = each.key
  connector_id       = try(each.value.worklytics_connector_id, "")
  psoxy_endpoint_url = module.aws-psoxy-long-auth-connectors[each.key].endpoint_url
  display_name       = try(each.value.worklytics_connector_name, "${each.value.display_name} via Psoxy")
  todo_step          = module.aws-psoxy-long-auth-connectors[each.key].next_todo_step

  settings_to_provide = {
    "AWS Psoxy Region"   = var.aws_region,
    "AWS Psoxy Role ARN" = module.psoxy-aws.api_caller_role_arn
  }
}



# END LONG ACCESS AUTH CONNECTORS

module "custom_rest_rules" {
  source = "../../modules/aws-ssm-rules"

  for_each = var.custom_rest_rules

  prefix    = "${var.aws_ssm_param_root_path}PSOXY_${upper(replace(each.key, "-", "_"))}_"
  file_path = each.value
}

# BEGIN BULK CONNECTORS

module "psoxy-bulk" {
  for_each = merge(module.worklytics_connector_specs.enabled_bulk_connectors, var.custom_bulk_connectors)

  source = "../../modules/aws-psoxy-bulk"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-bulk?ref=v0.4.57"

  environment_name                 = var.environment_name
  instance_id                      = each.key
  aws_account_id                   = var.aws_account_id
  aws_assume_role_arn              = var.aws_assume_role_arn
  aws_role_to_assume_when_testing  = var.provision_testing_infra ? module.psoxy-aws.api_caller_role_arn : null
  provision_iam_policy_for_testing = var.provision_testing_infra
  source_kind                      = each.value.source_kind
  aws_region                       = var.aws_region
  path_to_function_zip             = module.psoxy-aws.path_to_deployment_jar
  function_zip_hash                = module.psoxy-aws.deployment_package_hash
  psoxy_base_dir                   = var.psoxy_base_dir
  rules                            = each.value.rules
  global_parameter_arns            = module.global_secrets.secret_arns
  path_to_instance_ssm_parameters  = "${var.aws_ssm_param_root_path}PSOXY_${upper(replace(each.key, "-", "_"))}_"
  ssm_kms_key_ids                  = local.ssm_key_ids
  sanitized_accessor_role_names    = [module.psoxy-aws.api_caller_role_name]
  memory_size_mb                   = 1024
  log_retention_days               = var.log_retention_days
  sanitized_expiration_days        = var.bulk_sanitized_expiration_days
  input_expiration_days            = var.bulk_input_expiration_days

  example_file = try(each.value.example_file, null)

  environment_variables = merge(
    var.general_environment_variables,
    try(each.value.environment_variables, {}),
    {
      BUNDLE_FILENAME     = module.psoxy-aws.filename
      IS_DEVELOPMENT_MODE = contains(var.non_production_connectors, each.key)
    },
  )
}

module "psoxy-bulk-to-worklytics" {
  for_each = merge(module.worklytics_connector_specs.enabled_bulk_connectors,
  var.custom_bulk_connectors)

  source = "../../modules/worklytics-psoxy-connection-generic"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection-generic?ref=v0.4.57"

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

# BEGIN lookup builders
module "lookup_output" {
  for_each = var.lookup_table_builders

  source = "../../modules/aws-psoxy-output-bucket"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-output-bucket?ref=v0.4.57"

  environment_name              = var.environment_name
  instance_id                   = each.key
  iam_role_for_lambda_name      = module.psoxy-bulk[each.value.input_connector_id].instance_role_name
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


locals {
  all_instances = merge(
    { for instance in module.psoxy-google-workspace-connector : instance.instance_id => instance },
    { for instance in module.psoxy-bulk : instance.instance_id => instance },
    { for instance in module.aws-psoxy-long-auth-connectors : instance.instance_id => instance }
  )
}

output "instances" {
  value = local.all_instances
}

output "lookup_tables" {
  value = { for k, v in var.lookup_table_builders : k => module.lookup_output[k].output_bucket }
}

output "todos_1" {
  description = "List of todo steps to complete 1st, in markdown format."
  value = concat(
    values(module.google-workspace-connection)[*].todo,
    values(module.source_token_external_todo)[*].todo,
  )
}

output "todos_2" {
  description = "List of todo steps to complete 2nd, in markdown format."
  value = concat(
    values(module.worklytics-psoxy-connection-google-workspace)[*].todo,
    values(module.aws-psoxy-long-auth-connectors)[*].todo,
    values(module.psoxy-bulk)[*].todo,
  )
}

output "todos_3" {
  description = "List of todo steps to complete 3rd, in markdown format."
  value = concat(
    values(module.worklytics-psoxy-connection-google-workspace)[*].todo,
    values(module.worklytics-psoxy-connection)[*].todo,
    values(module.psoxy-bulk-to-worklytics)[*].todo,
  )
}

output "caller_role_arn" {
  description = "ARN of the AWS IAM role that can be assumed to invoke the Lambdas."
  value       = module.psoxy-aws.api_caller_role_arn
}

output "path_to_deployment_jar" {
  description = "Path to the package to deploy (JAR) as lambda."
  value       = module.psoxy-aws.path_to_deployment_jar
}

output "deployment_package_hash" {
  description = "Hash of deployment package."
  value       = module.psoxy-aws.deployment_package_hash
}