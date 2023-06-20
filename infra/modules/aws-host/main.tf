# NOTE: region used to be passed in as a variable; put it MUST match the region in which the lambda
# is provisioned, and that's implicit in the provider - so we should just infer from the provider
data "aws_region" "current" {}

module "env_id" {
  source = "../../modules/env-id"

  environment_name = var.environment_name
}

locals {
  base_config_path    = "${var.psoxy_base_dir}/configs/"
  host_platform_id    = "AWS"
  ssm_key_ids         = var.aws_ssm_key_id == null ? {} : { 0 : var.aws_ssm_key_id }
  instance_ssm_prefix = "${var.aws_ssm_param_root_path}${upper(module.env_id.id)}_"
}

module "psoxy" {
  source = "../../modules/aws"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws?ref=v0.4.25

  aws_account_id                 = var.aws_account_id
  region                         = data.aws_region.current.id
  psoxy_base_dir                 = var.psoxy_base_dir
  caller_aws_arns                = var.caller_aws_arns
  caller_gcp_service_account_ids = var.caller_gcp_service_account_ids
  force_bundle                   = var.force_bundle
  install_test_tool              = var.install_test_tool
  deployment_id                  = module.env_id.id
  api_function_name_prefix       = "${lower(module.env_id.id)}-"
}


# secrets shared across all instances
module "global_secrets" {
  source = "../../modules/aws-ssm-secrets"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-ssm-secrets?ref=v0.4.25

  path       = var.aws_ssm_param_root_path
  kms_key_id = var.aws_ssm_key_id
  secrets    = module.psoxy.secrets
}

module "instance_secrets" {
  for_each = var.api_connectors

  source = "../../modules/aws-ssm-secrets"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-ssm-secrets?ref=v0.4.25"
  # other possibly implementations:
  # source = "../hashicorp-vault-secrets"

  path       = "${local.instance_ssm_prefix}${replace(upper(each.key), "-", "_")}_"
  kms_key_id = var.aws_ssm_key_id
  secrets = { for v in each.value.secured_variables :
    v.name => {
      value       = v.value,
      description = try(v.description, null)
    }
  }
}

module "api_connector" {
  for_each = var.api_connectors

  source = "../../modules/aws-psoxy-rest"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-rest?ref=v0.4.25"

  environment_name                = var.environment_name
  instance_id                     = each.key
  source_kind                     = each.value.source_kind
  path_to_config                  = "${var.psoxy_base_dir}/configs/${each.value.source_kind}.yaml"
  path_to_function_zip            = module.psoxy.path_to_deployment_jar
  function_zip_hash               = module.psoxy.deployment_package_hash
  api_caller_role_arn             = module.psoxy.api_caller_role_arn
  example_api_calls               = each.value.example_api_calls
  aws_account_id                  = var.aws_account_id
  region                          = data.aws_region.current.id
  path_to_repo_root               = var.psoxy_base_dir
  todo_step                       = var.todo_step
  global_parameter_arns           = module.global_secrets.secret_arns
  path_to_instance_ssm_parameters = "${local.instance_ssm_prefix}${replace(upper(each.key), "-", "_")}_"
  ssm_kms_key_ids                 = local.ssm_key_ids
  target_host                     = each.value.target_host
  source_auth_strategy            = each.value.source_auth_strategy

  environment_variables = merge(
    var.general_environment_variables,
    try(each.value.environment_variables, {}),
    {
      PSEUDONYMIZE_APP_IDS = tostring(var.pseudonymize_app_ids)
      CUSTOM_RULES_SHA     = try(var.custom_api_connector_rules[each.key], null) != null ? filesha1(var.custom_api_connector_rules[each.key]) : null
      IS_DEVELOPMENT_MODE  = contains(var.non_production_connectors, each.key)
    }
  )
}

module "custom_api_connector_rules" {
  source = "../../modules/aws-ssm-rules"

  for_each = var.custom_api_connector_rules

  prefix    = "${local.instance_ssm_prefix}${replace(upper(each.key), "-", "_")}_"
  file_path = each.value
}

module "bulk_connector" {
  for_each = var.bulk_connectors

  source = "../../modules/aws-psoxy-bulk"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-bulk?ref=v0.4.25"

  aws_account_id                   = var.aws_account_id
  provision_iam_policy_for_testing = var.provision_testing_infra
  aws_role_to_assume_when_testing  = var.provision_testing_infra ? module.psoxy.api_caller_role_arn : null
  environment_name                 = var.environment_name
  instance_id                      = each.key
  source_kind                      = each.value.source_kind
  aws_region                       = data.aws_region.current.id
  path_to_function_zip             = module.psoxy.path_to_deployment_jar
  function_zip_hash                = module.psoxy.deployment_package_hash
  psoxy_base_dir                   = var.psoxy_base_dir
  rules                            = try(var.custom_bulk_connector_rules[each.key], each.value.rules)
  global_parameter_arns            = module.global_secrets.secret_arns
  path_to_instance_ssm_parameters  = "${local.instance_ssm_prefix}${replace(upper(each.key), "-", "_")}_"
  ssm_kms_key_ids                  = local.ssm_key_ids
  sanitized_accessor_role_names    = [module.psoxy.api_caller_role_name]
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


# BEGIN lookup tables
module "lookup_output" {
  for_each = var.lookup_table_builders

  source = "../../modules/aws-psoxy-output-bucket"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-output-bucket?ref=v0.4.25"

  environment_name              = var.environment_name
  instance_id                   = each.key
  iam_role_for_lambda_name      = module.bulk_connector[each.value.input_connector_id].instance_role_name
  sanitized_accessor_role_names = each.value.sanitized_accessor_role_names
}

locals {
  inputs_to_build_lookups_for = toset(distinct([for k, v in var.lookup_table_builders : v.input_connector_id]))
}

resource "aws_ssm_parameter" "additional_transforms" {
  for_each = local.inputs_to_build_lookups_for

  name = "${local.instance_ssm_prefix}${replace(upper(each.key), "-", "_")}_ADDITIONAL_TRANSFORMS"
  type = "String"
  value = yamlencode([for k, v in var.lookup_table_builders : {
    destinationBucketName : module.lookup_output[k].output_bucket
    rules : v.rules
  } if v.input_connector_id == each.key])
}

# END lookup tables

locals {
  api_instances = { for k, instance in module.api_connector :
    k => merge(
      instance,
      var.api_connectors[k]
    )
  }

  bulk_instances = { for k, instance in module.bulk_connector :
    k => merge(
      {
        sanitized_bucket_name : instance.sanitized_bucket
      },
      instance,
      var.bulk_connectors[k]
    )
  }

  all_instances = merge(local.api_instances, local.bulk_instances)
}

# script to test ALL connectors
resource "local_file" "test_all_script" {
  filename        = "test-all.sh"
  file_permission = "0770"
  content         = <<EOF
#!/bin/bash

echo "Testing API Connectors ..."

%{ for test_script in values(module.api_connector)[*].test_script ~}
./${test_script}
%{ endfor }

echo "Testing Bulk Connectors ..."

%{ for test_script in values(module.bulk_connector)[*].test_script ~}
./${test_script}
%{ endfor }
EOF
}
