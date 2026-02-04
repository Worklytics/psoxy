terraform {
  required_version = ">= 1.3, < 2.0"

  required_providers {
    aws = {
      version = ">= 4.22, < 5.0"
    }
  }
}

# NOTE: region used to be passed in as a variable; put it MUST match the region in which the lambda
# is provisioned, and that's implicit in the provider - so we should just infer from the provider
data "aws_region" "current" {}

module "env_id" {
  source = "../../modules/env-id"

  environment_name = var.environment_name
}

locals {
  host_platform_id    = "AWS"
  ssm_key_ids         = var.aws_ssm_key_id == null ? {} : { 0 : var.aws_ssm_key_id }
  instance_ssm_prefix = "${var.aws_ssm_param_root_path}${upper(module.env_id.id)}_"

  # VPC *requires* API Gateway v2, or calls just timeout
  use_api_gateway_v2 = var.vpc_config != null || var.use_api_gateway_v2

  has_enabled_webhook_collectors = length(keys(var.webhook_collectors)) > 0
  enable_webhook_testing         = var.provision_testing_infra && local.has_enabled_webhook_collectors

  api_connector_rules_files = merge(var.custom_api_connector_rules, { for k, v in var.api_connectors : k => v.rules_file if v.rules_file != null })

  # proxy caller role requires direct lambda access if API Gateway v2 is not used and there are API connectors
  caller_requires_direct_lambda_access = !local.use_api_gateway_v2 && length(module.api_connector) > 0
}

module "psoxy" {
  source = "../../modules/aws"

  aws_account_id                     = var.aws_account_id
  region                             = data.aws_region.current.id
  psoxy_base_dir                     = var.psoxy_base_dir
  caller_aws_arns                    = var.caller_aws_arns
  caller_gcp_service_account_ids     = var.caller_gcp_service_account_ids
  deployment_bundle                  = var.deployment_bundle
  force_bundle                       = var.force_bundle
  install_test_tool                  = var.install_test_tool
  deployment_id                      = module.env_id.id
  api_function_name_prefix           = "${lower(module.env_id.id)}-"
  use_api_gateway_v2                 = local.use_api_gateway_v2
  logs_kms_key_arn                   = var.logs_kms_key_arn
  iam_roles_permissions_boundary     = var.iam_roles_permissions_boundary
  provision_webhook_collection_infra = local.has_enabled_webhook_collectors
  enable_webhook_testing             = local.enable_webhook_testing
  webhook_allow_origins              = distinct(flatten([for v in var.webhook_collectors : v.allow_origins]))
}

resource "aws_iam_policy" "execution_lambda_to_caller" {
  count = local.caller_requires_direct_lambda_access ? 1 : 0

  name        = "${module.env_id.id}ExecuteLambdas" # TODO: change this name in next major version
  description = "Allow caller to invoke the lambda via function url"

  policy = jsonencode(
    {
      "Version" : "2012-10-17",
      "Statement" : [
        # allow caller to invoke the lambda via function url
        {
          "Action" : [
            # for new AWS accounts as of Oct 2025, both of these are required
            # see https://docs.aws.amazon.com/lambda/latest/dg/urls-auth.html 
            "lambda:InvokeFunctionUrl",
            "lambda:InvokeFunction"
          ],
          "Effect" : "Allow",
          "Resource" : [for k, v in module.api_connector : v.function_arn]
        }
      ],
  })

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

resource "aws_iam_role_policy_attachment" "invoker_url_lambda_execution" {
  count = local.caller_requires_direct_lambda_access ? 1 : 0

  role       = module.psoxy.api_caller_role_name
  policy_arn = aws_iam_policy.execution_lambda_to_caller[0].arn
}

# access to async output buckets
# this is independent of whether API connectors are otherwise invoked via API Gateway v2 or function urls
locals {
  async_output_buckets = [for k, v in module.api_connector : v.async_output_bucket_id if v.async_output_bucket_id != null]
}

resource "aws_iam_policy" "async_output_access" {
  count = length(local.async_output_buckets) > 0 ? 1 : 0

  name        = "${module.env_id.id}AsyncOutputAccess"
  description = "Allow caller to read from async output buckets (sanitized output)"

  policy = jsonencode(
    {
      "Version" : "2012-10-17",
      "Statement" : [for k, v in module.api_connector : {
        "Action" : ["s3:GetObject", "s3:ListBucket"],
        "Effect" : "Allow",
        "Resource" : ["arn:aws:s3:::${v.async_output_bucket_id}", "arn:aws:s3:::${v.async_output_bucket_id}/*"]
      } if v.async_output_bucket_id != null]
    }
  )
}

resource "aws_iam_role_policy_attachment" "async_output_access_to_caller" {
  count = length(local.async_output_buckets) > 0 ? 1 : 0

  role       = module.psoxy.api_caller_role_name
  policy_arn = aws_iam_policy.async_output_access[0].arn
}


# secrets shared across all instances
locals {
  path_to_shared_secrets = var.secrets_store_implementation == "aws_secrets_manager" ? var.aws_secrets_manager_path : var.aws_ssm_param_root_path

  # convert custom_side_outputs to the format expected by the psoxy module
  custom_original_side_outputs = { for k, v in var.custom_side_outputs :
    k => { bucket = v.ORIGINAL, allowed_readers = [] } if v.ORIGINAL != null
  }
  custom_sanitized_side_outputs = { for k, v in var.custom_side_outputs :
    k => { bucket = v.SANITIZED, allowed_readers = [] } if v.SANITIZED != null
  }
  required_side_output_config = {
    bucket          = null
    allowed_readers = [module.psoxy.api_caller_role_arn]
  }

  sanitized_side_outputs = { for k, v in var.api_connectors :
    k => try(v.enable_side_output, false) ? local.required_side_output_config : try(local.custom_sanitized_side_outputs[k], null)
  }
}

module "global_secrets_ssm" {
  count = var.secrets_store_implementation == "aws_ssm_parameter_store" ? 1 : 0

  source = "../../modules/aws-ssm-secrets"

  path       = local.path_to_shared_secrets
  kms_key_id = var.aws_ssm_key_id
  secrets    = module.psoxy.secrets
}

module "global_secrets_secrets_manager" {
  count = var.secrets_store_implementation == "aws_secrets_manager" ? 1 : 0

  source = "../../modules/aws-secretsmanager-secrets"

  path       = local.path_to_shared_secrets
  kms_key_id = var.aws_ssm_key_id
  secrets    = module.psoxy.secrets
}


module "instance_ssm_parameters" {
  for_each = var.api_connectors

  source = "../../modules/aws-ssm-secrets"

  path       = "${local.instance_ssm_prefix}${replace(upper(each.key), "-", "_")}_"
  kms_key_id = var.aws_ssm_key_id
  secrets = { for v in each.value.secured_variables :
    v.name => {
      value               = v.value,
      description         = try(v.description, null)
      sensitive           = try(v.sensitive, true)
      value_managed_by_tf = try(v.value_managed_by_tf, true) # ideally, would be `value != null`, but bc value is sensitive, Terraform doesn't allow for_each over map derived from sensitive values
    }
    if !(try(v.sensitive, true)) || var.secrets_store_implementation == "aws_ssm_parameter_store"
  }
}

module "instance_secrets_secrets_manager" {
  source = "../../modules/aws-secretsmanager-secrets"

  for_each = var.secrets_store_implementation == "aws_secrets_manager" ? var.api_connectors : {}

  path       = "${local.instance_ssm_prefix}${replace(upper(each.key), "-", "_")}_"
  kms_key_id = var.aws_ssm_key_id
  secrets = { for v in each.value.secured_variables :
    v.name => {
      value               = v.value,
      description         = try(v.description, null)
      sensitive           = try(v.sensitive, true)
      value_managed_by_tf = try(v.value_managed_by_tf, true) # ideally, would be `value != null`, but bc value is sensitive, Terraform doesn't allow for_each over map derived from sensitive values
    }
    if try(v.sensitive, true)
  }
}

# NOTE: parameter / secret ARNs passed into lambda modules, so that can write ONE policy for the
# exec role - instead of one for parameters, one for secrets, etc.
module "api_connector" {
  for_each = var.api_connectors

  source = "../../modules/aws-psoxy-rest"

  environment_name                      = var.environment_name
  instance_id                           = each.key
  source_kind                           = each.value.source_kind
  path_to_function_zip                  = module.psoxy.path_to_deployment_jar
  function_zip_hash                     = module.psoxy.deployment_package_hash
  function_env_kms_key_arn              = var.function_env_kms_key_arn
  logs_kms_key_arn                      = var.logs_kms_key_arn
  log_retention_days                    = var.log_retention_days
  api_caller_role_arn                   = module.psoxy.api_caller_role_arn
  example_api_calls                     = each.value.example_api_calls
  aws_account_id                        = var.aws_account_id
  region                                = data.aws_region.current.id
  path_to_repo_root                     = var.psoxy_base_dir
  secrets_store_implementation          = var.secrets_store_implementation
  global_parameter_arns                 = try(module.global_secrets_ssm[0].secret_arns, [])
  global_secrets_manager_secret_arns    = try(module.global_secrets_secrets_manager[0].secret_arns, {})
  path_to_instance_ssm_parameters       = "${local.instance_ssm_prefix}${replace(upper(each.key), "-", "_")}_"
  path_to_shared_ssm_parameters         = local.path_to_shared_secrets
  ssm_kms_key_ids                       = local.ssm_key_ids
  target_host                           = each.value.target_host
  source_auth_strategy                  = each.value.source_auth_strategy
  oauth_scopes                          = each.value.oauth_scopes_needed
  example_api_calls_user_to_impersonate = each.value.example_api_calls_user_to_impersonate
  vpc_config                            = var.vpc_config
  api_gateway_v2                        = module.psoxy.api_gateway_v2
  aws_lambda_execution_role_policy_arn  = var.aws_lambda_execution_role_policy_arn
  iam_roles_permissions_boundary        = var.iam_roles_permissions_boundary
  side_output_original                  = try(local.custom_original_side_outputs[each.key], null)
  side_output_sanitized                 = try(local.sanitized_side_outputs[each.key], null)
  enable_async_processing               = each.value.enable_async_processing
  memory_size_mb                        = each.value.enable_async_processing ? 1024 : 512 # default is 512; double it for async case, to give additional margin

  todos_as_local_files = var.todos_as_local_files
  todo_step            = var.todo_step

  environment_variables = merge(
    var.general_environment_variables,
    try(each.value.environment_variables, {}),
    {
      PSEUDONYMIZE_APP_IDS   = tostring(var.pseudonymize_app_ids)
      EMAIL_CANONICALIZATION = var.email_canonicalization
      CUSTOM_RULES_SHA       = try(local.api_connector_rules_files[each.key], null) != null ? filesha1(local.api_connector_rules_files[each.key]) : null
      IS_DEVELOPMENT_MODE    = contains(var.non_production_connectors, each.key)
    }
  )
}



module "custom_api_connector_rules" {
  source = "../../modules/aws-ssm-rules"

  for_each = local.api_connector_rules_files

  prefix    = "${local.instance_ssm_prefix}${replace(upper(each.key), "-", "_")}_"
  file_path = each.value
}

module "bulk_connector" {
  for_each = var.bulk_connectors

  source = "../../modules/aws-psoxy-bulk"

  aws_account_id                       = var.aws_account_id
  provision_iam_policy_for_testing     = var.provision_testing_infra
  aws_role_to_assume_when_testing      = var.provision_testing_infra ? module.psoxy.api_caller_role_arn : null
  environment_name                     = var.environment_name
  instance_id                          = each.key
  source_kind                          = each.value.source_kind
  aws_region                           = data.aws_region.current.id
  path_to_function_zip                 = module.psoxy.path_to_deployment_jar
  function_zip_hash                    = module.psoxy.deployment_package_hash
  function_env_kms_key_arn             = var.function_env_kms_key_arn
  logs_kms_key_arn                     = var.logs_kms_key_arn
  log_retention_days                   = var.log_retention_days
  psoxy_base_dir                       = var.psoxy_base_dir
  rules                                = try(var.custom_bulk_connector_rules[each.key], each.value.rules)
  rules_file                           = each.value.rules_file
  secrets_store_implementation         = var.secrets_store_implementation
  global_parameter_arns                = try(module.global_secrets_ssm[0].secret_arns, [])
  global_secrets_manager_secret_arns   = try(module.global_secrets_secrets_manager[0].secret_arns, {})
  path_to_instance_ssm_parameters      = "${local.instance_ssm_prefix}${replace(upper(each.key), "-", "_")}_"
  path_to_shared_ssm_parameters        = local.path_to_shared_secrets
  ssm_kms_key_ids                      = local.ssm_key_ids
  sanitized_accessor_role_names        = [module.psoxy.api_caller_role_name]
  memory_size_mb                       = coalesce(try(var.custom_bulk_connector_arguments[each.key].memory_size_mb, null), each.value.memory_size_mb, 1024)
  sanitized_expiration_days            = var.bulk_sanitized_expiration_days
  input_expiration_days                = var.bulk_input_expiration_days
  example_file                         = each.value.example_file
  example_files                        = try(each.value.example_files, [])
  instructions_template                = each.value.instructions_template
  vpc_config                           = var.vpc_config
  aws_lambda_execution_role_policy_arn = var.aws_lambda_execution_role_policy_arn
  provision_bucket_public_access_block = var.provision_bucket_public_access_block
  iam_roles_permissions_boundary       = var.iam_roles_permissions_boundary
  todos_as_local_files                 = var.todos_as_local_files



  environment_variables = merge(
    var.general_environment_variables,
    try(each.value.environment_variables, {}),
    {
      IS_DEVELOPMENT_MODE    = contains(var.non_production_connectors, each.key)
      EMAIL_CANONICALIZATION = var.email_canonicalization
    },
  )
}


module "webhook_collectors" {
  for_each = var.webhook_collectors

  source = "../../modules/aws-webhook-collector"

  environment_name                     = var.environment_name
  instance_id                          = each.key
  path_to_function_zip                 = module.psoxy.path_to_deployment_jar
  function_zip_hash                    = module.psoxy.deployment_package_hash
  function_env_kms_key_arn             = var.function_env_kms_key_arn
  logs_kms_key_arn                     = var.logs_kms_key_arn
  log_retention_days                   = var.log_retention_days
  sanitized_accessor_role_names        = [module.psoxy.api_caller_role_name]
  aws_account_id                       = var.aws_account_id
  path_to_repo_root                    = var.psoxy_base_dir
  secrets_store_implementation         = var.secrets_store_implementation
  global_parameter_arns                = try(module.global_secrets_ssm[0].secret_arns, [])
  global_secrets_manager_secret_arns   = try(module.global_secrets_secrets_manager[0].secret_arns, {})
  path_to_instance_ssm_parameters      = "${local.instance_ssm_prefix}${replace(upper(each.key), "-", "_")}_"
  path_to_shared_ssm_parameters        = local.path_to_shared_secrets
  ssm_kms_key_ids                      = local.ssm_key_ids
  vpc_config                           = var.vpc_config
  api_gateway_v2                       = module.psoxy.webhook_collection_gateway
  aws_lambda_execution_role_policy_arn = var.aws_lambda_execution_role_policy_arn
  iam_roles_permissions_boundary       = var.iam_roles_permissions_boundary
  test_caller_role_arn                 = module.psoxy.webhook_test_caller_role_arn
  rules_file                           = each.value.rules_file
  webhook_auth_public_keys             = each.value.auth_public_keys
  provision_auth_key                   = each.value.provision_auth_key
  output_path_prefix                   = each.value.output_path_prefix
  keep_warm_instances                  = try(each.value.keep_warm_instances, null)
  example_payload                      = try(each.value.example_payload, null)
  example_identity                     = try(each.value.example_identity, null)

  todos_as_local_files = var.todos_as_local_files

  environment_variables = merge(
    var.general_environment_variables,
    ## try(each.value.environment_variables, {}),
    {
      EMAIL_CANONICALIZATION = var.email_canonicalization
      ##CUSTOM_RULES_SHA       = try(var.custom_api_connector_rules[each.key], null) != null ? filesha1(var.custom_api_connector_rules[each.key]) : null
      IS_DEVELOPMENT_MODE = contains(var.non_production_connectors, each.key)
    }
  )
}

# Policy to allow test caller to invoke webhook collector urls and sign webhook requests
resource "aws_iam_policy" "invoke_webhook_collector_urls" {
  count = local.enable_webhook_testing ? 1 : 0

  name        = "${module.env_id.id}InvokeWebhookCollectorLambdaUrls"
  description = "Allow caller role to execute the webhook collector lambda url directly"

  policy = jsonencode(
    {
      "Version" : "2012-10-17",
      "Statement" : [
        { # allow test caller to invoke each webhook collector via its function url
          "Action" : [
            "lambda:InvokeFunctionUrl"
          ],
          "Effect" : "Allow",
          "Resource" : [for k, v in module.webhook_collectors : v.function_arn]
        },
        { # need to allow test caller to sign webhook requests using the auth key(s)
          "Action" : [
            "kms:Sign",
            "kms:GetPublicKey",
            "kms:DescribeKey"
          ],
          "Effect" : "Allow",
          "Resource" : flatten([for k, v in module.webhook_collectors : v.provisioned_auth_key_pairs])
        }
      ]
    }
  )
}

resource "aws_iam_role_policy_attachment" "invoke_webhook_collector_urls_to_test_role" {
  count = local.enable_webhook_testing ? 1 : 0

  policy_arn = aws_iam_policy.invoke_webhook_collector_urls[0].arn
  role       = element(split("/", module.psoxy.webhook_test_caller_role_arn), length(split("/", module.psoxy.webhook_test_caller_role_arn)) - 1)
}

# BEGIN lookup tables
module "lookup_output" {
  for_each = var.lookup_table_builders

  source = "../../modules/aws-psoxy-output-bucket"

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
    compressOutput : v.compress_output
  } if v.input_connector_id == each.key])
}

# END lookup tables

locals {
  api_instances = { for k, instance in module.api_connector :
    k => merge(
      {
        sanitized_bucket : try(instance.async_output_bucket_id, null),
      },
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

  webhook_collector_instances = { for k, instance in module.webhook_collectors :
    k => merge(
      instance,
      var.webhook_collectors[k]
    )
  }

  all_instances = merge(local.api_instances, local.bulk_instances, local.webhook_collector_instances)
}

# script to test ALL connectors
resource "local_file" "test_all_script" {
  count = var.todos_as_local_files ? 1 : 0

  filename        = "test-all.sh"
  file_permission = "755"
  content         = <<EOF
#!/bin/bash

echo "Testing API Connectors ..."

%{for test_script in values(module.api_connector)[*].test_script~}
%{if test_script != null}./${test_script}%{endif}
%{endfor}

%{if length(values(module.bulk_connector)) > 0~}
echo "Testing Bulk Connectors ..."
%{endif~}
%{for test_script in values(module.bulk_connector)[*].test_script~}
%{if test_script != null}./${test_script}%{endif}
%{endfor}

%{if local.enable_webhook_testing && local.has_enabled_webhook_collectors}
echo "Testing Webhook Collectors ..."
%{endif~}
%{for test_script in values(module.webhook_collectors)[*].test_script~}
%{if test_script != null}./${test_script}%{endif}
%{endfor}
EOF
}

