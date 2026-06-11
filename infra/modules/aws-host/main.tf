terraform {
  required_version = "~> 1.7"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 6.0"
    }
  }
}

# NOTE: region used to be passed in as a variable; put it MUST match the region in which the lambda
# is provisioned, and that's implicit in the provider - so we should just infer from the provider
data "aws_region" "current" {}

data "aws_caller_identity" "current" {}

data "aws_iam_session_context" "current" {
  arn = data.aws_caller_identity.current.arn
}

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

  terraform_principal_arn   = can(regex(":assumed-role/", data.aws_caller_identity.current.arn)) ? data.aws_iam_session_context.current.issuer_arn : data.aws_caller_identity.current.arn
  terraform_upload_role_arn = can(regex("^arn:aws:iam::\\d{12}:role/", local.terraform_principal_arn)) ? local.terraform_principal_arn : null

  test_aws_principal_arns = var.provision_testing_infra ? (
    var.test_aws_principal_arns != null ? var.test_aws_principal_arns : [local.terraform_principal_arn]
  ) : []

  api_connector_rules_files = merge(var.custom_api_connector_rules, { for k, v in var.api_connectors : k => v.rules_file if v.rules_file != null })

  # rules_file paths may be absolute, relative to the Terraform root module (deployment dir), or
  # relative to psoxy_base_dir (paths into the psoxy repo, eg docs/sources/...)
  _rules_file_references = distinct(concat(
    values(local.api_connector_rules_files),
    [for k, v in var.bulk_connectors : v.rules_file if try(v.rules_file, null) != null],
    [for k, v in var.webhook_collectors : v.rules_file if try(v.rules_file, null) != null],
  ))

  _resolved_rules_file_paths = {
    for rules_path in local._rules_file_references : rules_path => (
      startswith(rules_path, "/") ? rules_path : (
        fileexists("${path.root}/${rules_path}") ? "${path.root}/${rules_path}" : (
          fileexists("${coalesce(var.psoxy_base_dir, "")}${rules_path}") ? "${coalesce(var.psoxy_base_dir, "")}${rules_path}" :
          "${path.root}/${rules_path}"
        )
      )
    )
  }

  api_connector_rules_file_paths = {
    for k, rules_path in local.api_connector_rules_files : k => local._resolved_rules_file_paths[rules_path]
  }

  bulk_connector_rules_file_paths = {
    for k, v in var.bulk_connectors : k => local._resolved_rules_file_paths[v.rules_file]
    if try(v.rules_file, null) != null
  }

  webhook_collector_rules_file_paths = {
    for k, v in var.webhook_collectors : k => local._resolved_rules_file_paths[v.rules_file]
    if try(v.rules_file, null) != null
  }

  # API connectors with rules_raw that don't have file-based overrides
  api_connector_rules_raw = {
    for k, v in var.api_connectors : k => v.rules_raw
    if try(v.rules_raw, null) != null && !contains(keys(local.api_connector_rules_files), k)
  }

  # proxy caller role requires direct lambda access if API Gateway v2 is not used and there are API connectors
  caller_requires_direct_lambda_access = !local.use_api_gateway_v2 && length(module.api_connector) > 0
}

module "psoxy" {
  source = "../../modules/aws"

  aws_account_id                     = var.aws_account_id
  region                             = data.aws_region.current.region
  psoxy_base_dir                     = var.psoxy_base_dir
  caller_aws_arns                    = var.caller_aws_arns
  test_aws_principal_arns            = local.test_aws_principal_arns
  caller_gcp_service_account_ids     = var.caller_gcp_service_account_ids
  deployment_bundle                  = var.deployment_bundle
  deployment_bundle_hash             = var.deployment_bundle_hash
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
  artifacts_bucket_name              = var.artifacts_bucket_name
  allowed_data_access_ip_blocks      = var.allowed_data_access_ip_blocks
  allowed_webhook_ip_blocks          = var.allowed_webhook_ip_blocks
}

# secrets shared across all instances
locals {
  path_to_shared_secrets = var.secrets_store_implementation == "aws_secrets_manager" ? var.aws_secrets_manager_path : var.aws_ssm_param_root_path

  # S3 object prefixes use '/' hierarchy (see gcp-host for rationale).
  resource_path_root = trimsuffix(trimprefix(coalesce(
    local.path_to_shared_secrets != "" ? local.path_to_shared_secrets : null,
    trimsuffix(local.instance_ssm_prefix, "_")
  ), "/"), "_")
  shared_resource_path = "${local.resource_path_root}/"
  connector_instance_resource_path = { for k, v in merge(var.api_connectors, var.bulk_connectors, var.webhook_collectors) :
    k => "${local.shared_resource_path}${replace(upper(k), "-", "_")}/"
  }

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

  source = "../../modules/aws-proxy-api"

  environment_name                      = var.environment_name
  new_relic_account_id                  = var.new_relic_account_id
  instance_id                           = each.key
  source_kind                           = each.value.source_kind
  path_to_function_zip                  = module.psoxy.path_to_deployment_jar
  function_zip_hash                     = module.psoxy.deployment_package_hash
  function_env_kms_key_arn              = var.function_env_kms_key_arn
  logs_kms_key_arn                      = var.logs_kms_key_arn
  log_retention_days                    = var.log_retention_days
  api_caller_role_arn                   = module.psoxy.api_caller_role_arn
  example_api_calls                     = each.value.example_api_calls
  example_api_requests                  = try(each.value.example_api_requests, [])
  aws_account_id                        = var.aws_account_id
  region                                = data.aws_region.current.region
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

  todos_as_local_files          = var.todos_as_local_files
  todo_step                     = var.todo_step
  timeout_seconds               = coalesce(try(each.value.timeout_seconds, null), 180)
  allowed_data_access_ip_blocks = var.allowed_data_access_ip_blocks

  environment_variables = merge(
    {
      PSEUDONYMIZE_APP_IDS   = tostring(var.pseudonymize_app_ids)
      EMAIL_CANONICALIZATION = var.email_canonicalization
      CUSTOM_RULES_SHA = try(local.api_connector_rules_file_paths[each.key], null) != null ? filesha1(local.api_connector_rules_file_paths[each.key]) : (
        try(local.api_connector_rules_raw[each.key], null) != null ? sha1(local.api_connector_rules_raw[each.key]) : null
      )
      IS_DEVELOPMENT_MODE = contains(var.non_production_connectors, each.key)
    },
    try(each.value.environment_variables, {}),
    var.general_environment_variables,
  )

  remote_resource_bucket        = var.enable_remote_resources ? module.psoxy.artifacts_bucket_name : null
  remote_resource_instance_path = var.enable_remote_resources ? local.connector_instance_resource_path[each.key] : null
  remote_resource_shared_path   = var.enable_remote_resources ? local.shared_resource_path : null
}



module "custom_api_connector_rules" {
  source = "../../modules/aws-ssm-rules"

  for_each = local.api_connector_rules_files

  prefix    = "${local.instance_ssm_prefix}${replace(upper(each.key), "-", "_")}_"
  file_path = local.api_connector_rules_file_paths[each.key]
}

# Rules provisioned from rules_raw (content string, not file path)
module "api_connector_rules_raw" {
  source = "../../modules/aws-ssm-rules"

  for_each = local.api_connector_rules_raw

  prefix  = "${local.instance_ssm_prefix}${replace(upper(each.key), "-", "_")}_"
  content = each.value
}

module "bulk_connector" {
  for_each = var.bulk_connectors

  source = "../../modules/aws-proxy-bulk"

  aws_account_id                        = var.aws_account_id
  test_aws_principal_arns               = local.test_aws_principal_arns
  provision_iam_policy_for_testing      = var.provision_testing_infra
  aws_principal_arn_when_testing        = var.provision_testing_infra ? module.psoxy.api_caller_role_arn : null
  aws_write_role_to_assume_when_testing = var.provision_testing_infra ? local.terraform_upload_role_arn : null
  environment_name                      = var.environment_name
  new_relic_account_id                  = var.new_relic_account_id
  instance_id                           = each.key
  source_kind                           = each.value.source_kind
  aws_region                            = data.aws_region.current.region
  path_to_function_zip                  = module.psoxy.path_to_deployment_jar
  function_zip_hash                     = module.psoxy.deployment_package_hash
  function_env_kms_key_arn              = var.function_env_kms_key_arn
  logs_kms_key_arn                      = var.logs_kms_key_arn
  log_retention_days                    = var.log_retention_days
  psoxy_base_dir                        = var.psoxy_base_dir
  rules = (
    try(var.custom_bulk_connector_rules[each.key], null) != null ? var.custom_bulk_connector_rules[each.key] :
    each.value.rules
  )
  rules_file = (
    # rules_file only applies when custom_bulk_connector_rules and rules_raw don't take precedence
    try(var.custom_bulk_connector_rules[each.key], null) == null && try(each.value.rules_raw, null) == null ? try(local.bulk_connector_rules_file_paths[each.key], null) : null
  )
  secrets_store_implementation         = var.secrets_store_implementation
  global_parameter_arns                = try(module.global_secrets_ssm[0].secret_arns, [])
  global_secrets_manager_secret_arns   = try(module.global_secrets_secrets_manager[0].secret_arns, {})
  path_to_instance_ssm_parameters      = "${local.instance_ssm_prefix}${replace(upper(each.key), "-", "_")}_"
  path_to_shared_ssm_parameters        = local.path_to_shared_secrets
  ssm_kms_key_ids                      = local.ssm_key_ids
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
    {
      IS_DEVELOPMENT_MODE    = contains(var.non_production_connectors, each.key)
      EMAIL_CANONICALIZATION = var.email_canonicalization
    },
    try(each.value.environment_variables, {}),
    # If rules_raw is set and there's no custom override, pass it as RULES env var
    try(var.custom_bulk_connector_rules[each.key], null) == null && try(each.value.rules_raw, null) != null ? {
      RULES = each.value.rules_raw
    } : {},
    var.general_environment_variables
  )

  remote_resource_bucket        = var.enable_remote_resources ? module.psoxy.artifacts_bucket_name : null
  remote_resource_instance_path = var.enable_remote_resources ? local.connector_instance_resource_path[each.key] : null
  remote_resource_shared_path   = var.enable_remote_resources ? local.shared_resource_path : null
}


module "webhook_collectors" {
  for_each = var.webhook_collectors

  source = "../../modules/aws-webhook-collector"

  environment_name                     = var.environment_name
  new_relic_account_id                 = var.new_relic_account_id
  instance_id                          = each.key
  path_to_function_zip                 = module.psoxy.path_to_deployment_jar
  function_zip_hash                    = module.psoxy.deployment_package_hash
  function_env_kms_key_arn             = var.function_env_kms_key_arn
  logs_kms_key_arn                     = var.logs_kms_key_arn
  log_retention_days                   = var.log_retention_days
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
  rules_file                           = try(local.webhook_collector_rules_file_paths[each.key], null)
  webhook_auth_public_keys             = each.value.auth_public_keys
  provision_auth_key                   = each.value.provision_auth_key
  output_path_prefix                   = each.value.output_path_prefix
  keep_warm_instances                  = try(each.value.keep_warm_instances, null)
  example_payload                      = try(each.value.example_payload, null)
  example_identity                     = try(each.value.example_identity, null)

  todos_as_local_files      = var.todos_as_local_files
  allowed_webhook_ip_blocks = var.allowed_webhook_ip_blocks

  environment_variables = merge(
    {
      EMAIL_CANONICALIZATION = var.email_canonicalization
      ##CUSTOM_RULES_SHA       = try(var.custom_api_connector_rules[each.key], null) != null ? filesha1(var.custom_api_connector_rules[each.key]) : null
      IS_DEVELOPMENT_MODE = contains(var.non_production_connectors, each.key)
    },
    var.general_environment_variables,
  )

  remote_resource_bucket        = var.enable_remote_resources ? module.psoxy.artifacts_bucket_name : null
  remote_resource_instance_path = var.enable_remote_resources ? local.connector_instance_resource_path[each.key] : null
  remote_resource_shared_path   = var.enable_remote_resources ? local.shared_resource_path : null
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
        },
        { # allow test caller to read from sanitized output buckets to verify collection
          "Action" : [
            "s3:ListBucket",
            "s3:GetObject"
          ],
          "Effect" : "Allow",
          "Resource" : flatten([for k, v in module.webhook_collectors : [
            "arn:aws:s3:::${v.output_sanitized_bucket_id}",
            "arn:aws:s3:::${v.output_sanitized_bucket_id}/*"
          ]])
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

  source = "../../modules/aws-proxy-output-bucket"

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

# Host-level IAM policies (consolidated per principal/role, rather than per connector instance).
locals {
  # Plan-time signal for whether caller output-bucket read is configured. Do not use bucket names
  # from module outputs here — those may be unknown until apply (eg, bucket_prefix).
  caller_has_configured_output_buckets = (
    length(var.bulk_connectors) > 0 ||
    length(var.webhook_collectors) > 0 ||
    length(var.lookup_table_builders) > 0 ||
    length([for k, v in var.api_connectors : k if try(v.enable_async_processing, false)]) > 0 ||
    length([for k, v in local.sanitized_side_outputs : k if v != null]) > 0
  )

  caller_readable_s3_bucket_ids = distinct(compact(concat(
    [for k, v in module.bulk_connector : v.sanitized_bucket],
    [for k, v in module.webhook_collectors : v.output_sanitized_bucket_id],
    [for k, v in module.api_connector : v.async_output_bucket_id if try(v.async_output_bucket_id, null) != null],
    [for k, v in module.api_connector : v.side_output_sanitized_bucket_id if try(v.side_output_sanitized_bucket_id, null) != null],
    [for k, v in module.lookup_output : v.output_bucket],
  )))

  caller_output_bucket_read_resources = flatten([
    for bucket_id in local.caller_readable_s3_bucket_ids : [
      "arn:aws:s3:::${bucket_id}",
      "arn:aws:s3:::${bucket_id}/*",
    ]
  ])

  # Lookup-table accessor roles (other than the Psoxy caller) need read access only to their
  # lookup bucket(s), not to all output buckets.
  lookup_tables_with_non_caller_accessor_roles = {
    for lookup_id, config in var.lookup_table_builders :
    lookup_id => [
      for role_name in config.sanitized_accessor_role_names :
      role_name if role_name != module.psoxy.api_caller_role_name
    ]
    if length([
      for role_name in config.sanitized_accessor_role_names :
      role_name if role_name != module.psoxy.api_caller_role_name
    ]) > 0
  }

  lookup_bucket_read_attachments = merge([
    for lookup_id, role_names in local.lookup_tables_with_non_caller_accessor_roles : {
      for role_name in toset(role_names) :
      "${lookup_id}-${role_name}" => {
        lookup_id = lookup_id
        role_name = role_name
      }
    }
  ]...)

  provision_psoxy_caller_access_policy = local.caller_requires_direct_lambda_access || local.caller_has_configured_output_buckets
}

data "aws_iam_policy_document" "lookup_bucket_read" {
  for_each = local.lookup_tables_with_non_caller_accessor_roles

  statement {
    sid    = "ReadLookupBucket"
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:ListBucket",
    ]
    resources = [
      "arn:aws:s3:::${module.lookup_output[each.key].output_bucket}",
      "arn:aws:s3:::${module.lookup_output[each.key].output_bucket}/*",
    ]
  }
}

data "aws_iam_policy_document" "psoxy_caller_access" {
  count = local.provision_psoxy_caller_access_policy ? 1 : 0

  dynamic "statement" {
    for_each = local.caller_requires_direct_lambda_access ? [1] : []
    content {
      sid    = "InvokeApiConnectors"
      effect = "Allow"
      actions = [
        "lambda:InvokeFunctionUrl",
        "lambda:InvokeFunction",
      ]
      resources = [for k, v in module.api_connector : v.function_arn]
    }
  }

  dynamic "statement" {
    for_each = local.caller_has_configured_output_buckets ? [1] : []
    content {
      sid    = "ReadOutputBuckets"
      effect = "Allow"
      actions = [
        "s3:GetObject",
        "s3:ListBucket",
      ]
      resources = local.caller_output_bucket_read_resources
    }
  }
}

resource "aws_iam_policy" "psoxy_caller_access" {
  count = local.provision_psoxy_caller_access_policy ? 1 : 0

  name        = "${module.env_id.id}CallerAccess"
  description = "Allow Psoxy caller to invoke API connectors and read sanitized output buckets"

  policy = data.aws_iam_policy_document.psoxy_caller_access[0].json

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

resource "aws_iam_role_policy_attachment" "psoxy_caller_access" {
  count = local.provision_psoxy_caller_access_policy ? 1 : 0

  role       = module.psoxy.api_caller_role_name
  policy_arn = aws_iam_policy.psoxy_caller_access[0].arn
}

resource "aws_iam_policy" "lookup_bucket_read" {
  for_each = local.lookup_tables_with_non_caller_accessor_roles

  name        = "${module.env_id.id}LookupBucketRead_${replace(each.key, "-", "_")}"
  description = "Allow read access to lookup table bucket: ${module.lookup_output[each.key].output_bucket}"

  policy = data.aws_iam_policy_document.lookup_bucket_read[each.key].json

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

resource "aws_iam_role_policy_attachment" "lookup_bucket_read" {
  for_each = local.lookup_bucket_read_attachments

  role       = each.value.role_name
  policy_arn = aws_iam_policy.lookup_bucket_read[each.value.lookup_id].arn
}

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

output "artifacts_bucket_name" {
  value = module.psoxy.artifacts_bucket_name
}
