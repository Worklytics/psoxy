terraform {
  required_providers {
    # for the infra that will host Psoxy instances
    aws = {
      source  = "hashicorp/aws"
      version = "~> 4.12"
    }

    # for the API connections to Google Workspace
    google = {
      version = ">= 3.74, <= 5.0"
    }
  }
}

locals {
  base_config_path = "${var.psoxy_base_dir}/configs/"
}

module "worklytics_connector_specs" {
  source = "../worklytics-connector-specs"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-connector-specs?ref=v0.4.7

  enabled_connectors            = var.enabled_connectors
  google_workspace_example_user = var.google_workspace_example_user
}

module "psoxy-aws" {
  source = "../aws"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-connector-specs?ref=v0.4.7

  aws_account_id                 = var.aws_account_id
  psoxy_base_dir                 = var.psoxy_base_dir
  caller_aws_arns                = var.caller_aws_arns
  caller_gcp_service_account_ids = var.caller_gcp_service_account_ids
}

# secrets shared across all instances
module "global_secrets" {
  source = "../aws-ssm-secrets"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/google-workspace-dwd-connection?ref=v0.4.7

  secrets = module.psoxy-aws.secrets
}

moved {
  from = module.psoxy-aws.aws_ssm_parameter.salt
  to   = module.global_secrets.aws_ssm_parameter.secret["PSOXY_SALT"]
}

moved {
  from = module.psoxy-aws.aws_ssm_parameter.encryption_key
  to   = module.global_secrets.aws_ssm_parameter.secret["PSOXY_ENCRYPTION_KEY"]
}

module "google-workspace-connection" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../google-workspace-dwd-connection"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/google-workspace-dwd-connection?ref=v0.4.7

  project_id                   = var.gcp_project_id
  connector_service_account_id = "psoxy-${each.key}"
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

  source = "../gcp-sa-auth-key"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-sa-auth-key?ref=v0.4.7"

  service_account_id = module.google-workspace-connection[each.key].service_account_id
}

module "sa-key-secrets" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../aws-ssm-secrets"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-ssm-secrets?ref=v0.4.7"
  # other possibly implementations:
  # source = "../hashicorp-vault-secrets"

  secrets = {
    "PSOXY_${replace(upper(each.key), "-", "_")}_SERVICE_ACCOUNT_KEY" : {
      value       = module.google-workspace-connection-auth[each.key].key_value
      description = "GCP service account key for ${each.key} connector"
    }
  }
}

module "psoxy-google-workspace-connector" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../aws-psoxy-rest"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-rest?ref=v0.4.7"

  function_name                         = "psoxy-${each.key}"
  source_kind                           = each.key
  path_to_function_zip                  = module.psoxy-aws.path_to_deployment_jar
  function_zip_hash                     = module.psoxy-aws.deployment_package_hash
  path_to_config                        = "${local.base_config_path}/${each.key}.yaml"
  api_caller_role_arn                   = module.psoxy-aws.api_caller_role_arn
  aws_assume_role_arn                   = var.aws_assume_role_arn
  aws_account_id                        = var.aws_account_id
  path_to_repo_root                     = var.psoxy_base_dir
  example_api_calls                     = each.value.example_api_calls
  example_api_calls_user_to_impersonate = each.value.example_api_calls_user_to_impersonate
  global_parameter_arns                 = module.global_secrets.secret_arns
  todo_step                             = module.google-workspace-connection[each.key].next_todo_step

  environment_variables = {
    IS_DEVELOPMENT_MODE = contains(var.non_production_connectors, each.key)
  }
}


module "worklytics-psoxy-connection-google-workspace" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../worklytics-psoxy-connection-aws"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection-aws?ref=v0.4.7"

  psoxy_instance_id  = each.key
  psoxy_endpoint_url = module.psoxy-google-workspace-connector[each.key].endpoint_url
  display_name       = "${each.value.display_name} via Psoxy${var.connector_display_name_suffix}"
  aws_region         = var.aws_region
  aws_role_arn       = module.psoxy-aws.api_caller_role_arn
  todo_step          = module.psoxy-google-workspace-connector[each.key].next_todo_step
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

  name        = "PSOXY_${upper(replace(each.value.connector_name, "-", "_"))}_${upper(each.value.secret_name)}"
  type        = "SecureString"
  description = "Stores the value of ${upper(each.value.secret_name)} for `psoxy-${each.value.connector_name}`"
  value       = sensitive("TODO: fill me with the proper value for ${upper(each.value.secret_name)}!! (via AWS console)")

  lifecycle {
    ignore_changes = [
      value # we expect this to be filled via Console, so don't want to overwrite it with the dummy value if changed
    ]
  }
}

module "parameter-fill-instructions" {
  for_each = local.long_access_parameters

  source = "../aws-ssm-fill-md"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-ssm-fill-md?ref=v0.4.7"

  region         = var.aws_region
  parameter_name = aws_ssm_parameter.long-access-secrets[each.key].name
}

module "source_token_external_todo" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors_todos

  source = "../source-token-external-todo"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/source-token-external-todo?ref=v0.4.7"

  source_id                         = each.key
  connector_specific_external_steps = each.value.external_token_todo
  todo_step                         = 1

  additional_steps = [for parameter_ref in local.long_access_parameters_by_connector[each.key] : module.parameter-fill-instructions[parameter_ref].todo_markdown]
}

module "aws-psoxy-long-auth-connectors" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  source = "../aws-psoxy-rest"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-rest?ref=v0.4.7"

  function_name                  = "psoxy-${each.key}"
  path_to_function_zip           = module.psoxy-aws.path_to_deployment_jar
  function_zip_hash              = module.psoxy-aws.deployment_package_hash
  path_to_config                 = "${local.base_config_path}/${each.value.source_kind}.yaml"
  aws_assume_role_arn            = var.aws_assume_role_arn
  aws_account_id                 = var.aws_account_id
  api_caller_role_arn            = module.psoxy-aws.api_caller_role_arn
  source_kind                    = each.value.source_kind
  path_to_repo_root              = var.psoxy_base_dir
  example_api_calls              = each.value.example_api_calls
  reserved_concurrent_executions = each.value.reserved_concurrent_executions
  global_parameter_arns          = module.global_secrets.secret_arns
  function_parameters            = each.value.secured_variables
  todo_step                      = module.source_token_external_todo[each.key].next_todo_step

  environment_variables = {
    IS_DEVELOPMENT_MODE = contains(var.non_production_connectors, each.key)
  }
}

module "worklytics-psoxy-connection" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  source = "../worklytics-psoxy-connection-aws"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection-aws?ref=v0.4.7"

  psoxy_instance_id  = each.key
  psoxy_endpoint_url = module.aws-psoxy-long-auth-connectors[each.key].endpoint_url
  display_name       = "${each.value.display_name} via Psoxy${var.connector_display_name_suffix}"
  aws_region         = var.aws_region
  aws_role_arn       = module.psoxy-aws.api_caller_role_arn
  todo_step          = module.aws-psoxy-long-auth-connectors[each.key].next_todo_step
}

# END LONG ACCESS AUTH CONNECTORS

module "psoxy-bulk" {
  for_each = merge(module.worklytics_connector_specs.enabled_bulk_connectors,
  var.custom_bulk_connectors)

  source = "../../modules/aws-psoxy-bulk"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-bulk?ref=v0.4.7"

  aws_account_id        = var.aws_account_id
  aws_assume_role_arn   = var.aws_assume_role_arn
  instance_id           = each.key
  source_kind           = each.value.source_kind
  aws_region            = var.aws_region
  path_to_function_zip  = module.psoxy-aws.path_to_deployment_jar
  function_zip_hash     = module.psoxy-aws.deployment_package_hash
  api_caller_role_arn   = module.psoxy-aws.api_caller_role_arn
  api_caller_role_name  = module.psoxy-aws.api_caller_role_name
  psoxy_base_dir        = var.psoxy_base_dir
  rules                 = each.value.rules
  global_parameter_arns = module.global_secrets.secret_arns
  environment_variables = {
    IS_DEVELOPMENT_MODE = contains(var.non_production_connectors, each.key)
  }
}


module "psoxy_lookup_tables_builders" {
  for_each = var.lookup_table_builders

  source = "../../modules/aws-psoxy-bulk-existing"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-existing?ref=v0.4.7"

  input_bucket                  = module.psoxy-bulk[each.value.input_connector_id].input_bucket
  aws_account_id                = var.aws_account_id
  instance_id                   = each.key
  aws_region                    = var.aws_region
  path_to_function_zip          = module.psoxy-aws.path_to_deployment_jar
  function_zip_hash             = module.psoxy-aws.deployment_package_hash
  psoxy_base_dir                = var.psoxy_base_dir
  rules                         = each.value.rules
  global_parameter_arns         = module.global_secrets.secret_arns
  sanitized_accessor_role_names = each.value.sanitized_accessor_role_names
  environment_variables = {
    IS_DEVELOPMENT_MODE = contains(var.non_production_connectors, each.key)
  }
}


output "lookup_tables" {
  value = { for k,v in var.lookup_table_builders : k => module.psoxy_lookup_tables_builders[k].output_bucket }
}
