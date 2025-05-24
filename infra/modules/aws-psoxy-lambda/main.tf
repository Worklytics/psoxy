# provision a Psoxy instance into AWS account

terraform {
  required_providers {
    aws = {
      version = ">= 4.12, < 5.0"
    }
  }
}

module "env_id" {
  source = "../env-id"

  environment_name          = var.environment_name
  supported_word_delimiters = ["-"]
  preferred_word_delimiter  = "-"
}

locals {
  salt_parameter_name_suffix = "PSOXY_SALT"
  function_name              = "${module.env_id.id}-${var.instance_id}"

  kms_key_ids_to_allow = merge(
    var.ssm_kms_key_ids,
    var.kms_keys_to_allow
  )
}


locals {
  instance_ssm_prefix_default    = "${upper(replace(local.function_name, "-", "_"))}_"
  instance_ssm_prefix            = coalesce(var.path_to_instance_ssm_parameters, local.instance_ssm_prefix_default)
  is_instance_ssm_prefix_default = local.instance_ssm_prefix == local.instance_ssm_prefix_default
  instance_ssm_prefix_with_slash = startswith(local.instance_ssm_prefix, "/") ? local.instance_ssm_prefix : "/${local.instance_ssm_prefix}"

  bundle_from_s3  = startswith(var.path_to_function_zip, "s3://")
  s3_bucket       = local.bundle_from_s3 ? regex("s3://([^/]+)/.*", var.path_to_function_zip)[0] : null
  s3_key          = local.bundle_from_s3 ? regex("s3://[^/]+/(.*)", var.path_to_function_zip)[0] : null
  bundle_filename = try(regex(".*/([^/]+)", var.path_to_function_zip)[0], var.path_to_function_zip)
}


resource "aws_lambda_function" "instance" {
  function_name                  = local.function_name
  role                           = aws_iam_role.iam_for_lambda.arn
  architectures                  = ["arm64"] # 20% cheaper per ms exec time than x86_64
  runtime                        = "java17"
  filename                       = local.bundle_from_s3 ? null : var.path_to_function_zip
  s3_bucket                      = local.s3_bucket # null if local file
  s3_key                         = local.s3_key    # null if local file
  source_code_hash               = var.function_zip_hash
  handler                        = var.handler_class
  timeout                        = var.timeout_seconds
  memory_size                    = var.memory_size_mb
  reserved_concurrent_executions = coalesce(var.reserved_concurrent_executions, -1)
  kms_key_arn                    = var.function_env_kms_key_arn


  ephemeral_storage {
    size = var.ephemeral_storage_mb
  }
  # TODO: aws provider v5 this becomes
  # ephemeral_storage              = var.ephemeral_storage_mb


  dynamic "vpc_config" {
    for_each = var.vpc_config[*]

    content {
      subnet_ids         = vpc_config.value.subnet_ids
      security_group_ids = vpc_config.value.security_group_ids
      # q: why does the following not validate??
      #ipv6_allowed_for_dual_stack = vpc_config.value.ipv6_allowed_for_dual_stack
    }
  }

  environment {
    variables = merge(
      var.path_to_config == null ? {} : yamldecode(file(var.path_to_config)),
      var.environment_variables,
      {
        EXECUTION_ROLE  = aws_iam_role.iam_for_lambda.arn, # q: used for anything? doesn't seem accessed by Java code ...
        BUNDLE_FILENAME = local.bundle_filename
        SECRETS_STORE   = upper(var.secrets_store_implementation)
      },
      # only set env vars for config paths if non-default values
      length(var.path_to_shared_ssm_parameters) > 1 ? { PATH_TO_SHARED_CONFIG = var.path_to_shared_ssm_parameters } : {},
      local.is_instance_ssm_prefix_default ? {} : { PATH_TO_INSTANCE_CONFIG = var.path_to_instance_ssm_parameters }
    )
  }

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

# cloudwatch group per lambda function
resource "aws_cloudwatch_log_group" "lambda_log" {
  name              = "/aws/lambda/${aws_lambda_function.instance.function_name}"
  retention_in_days = var.log_retention_in_days
  kms_key_id        = var.logs_kms_key_arn

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

resource "aws_iam_role" "iam_for_lambda" {
  name        = "${local.function_name}_Exec"
  description = "execution role for psoxy instance"

  assume_role_policy = jsonencode({
    "Version" : "2012-10-17",
    "Statement" : [
      {
        "Action" : "sts:AssumeRole",
        "Principal" : {
          "Service" : "lambda.amazonaws.com"
        },
        "Effect" : "Allow",
        "Sid" : ""
      }
    ]
  })

  permissions_boundary = var.iam_roles_permissions_boundary

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

locals {
  # which policy we attach depends on lambda's need
  # - AWSLambdaBasicExecutionRole usually sufficient
  # - AWSLambdaVPCAccessExecutionRole if lambda is configured to be on a VPC (if lacks this, deploy fails bc exec role can't create network interface)
  lambda_execution_role_aws_managed_policy = var.vpc_config == null ? "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole" : "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}



resource "aws_iam_role_policy_attachment" "basic" {
  role       = aws_iam_role.iam_for_lambda.name
  policy_arn = coalesce(var.aws_lambda_execution_role_policy_arn, local.lambda_execution_role_aws_managed_policy)
}


# NOTE: these are known at plan time, allowing all the locals below to also be known at plan time
#   (if you take region from lambda/role, terraform plan shows the IAM policy as 'Known after apply')
data "aws_region" "current" {}
data "aws_caller_identity" "current" {}

# bc 'key_id' may be ARN, id, or alias, we need to look up the ARN for each key - as IAM policy must
# be specified with ARNs
data "aws_kms_key" "keys_to_allow" {
  for_each = local.kms_key_ids_to_allow

  key_id = each.value
}

locals {
  param_arn_prefix = "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter${local.instance_ssm_prefix_with_slash}"

  secret_arn_prefix = "arn:aws:secretsmanager:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:secret:${local.instance_ssm_prefix}"

  kms_keys_to_allow_arns = distinct(concat(
    [for k in data.aws_kms_key.keys_to_allow : k.arn],
    var.function_env_kms_key_arn == null ? [] : [var.function_env_kms_key_arn]
  ))

  local_ssm_param_statements = [{
    Sid = "ReadInstanceSSMParameters"
    Action = [
      "ssm:GetParameter",
      "ssm:GetParameterVersion",
      "ssm:GetParameters",
      "ssm:GetParametersByPath",
      "ssm:PutParameter",
      "ssm:DeleteParameter" # delete locks, bad access tokens, etc
    ]
    Effect = "Allow"
    Resource = [
      "${local.param_arn_prefix}*" # wildcard to match all params corresponding to this function
    ]
  }]

  local_secrets_manager_statements = var.secrets_store_implementation == "aws_secrets_manager" ? [{
    Sid = "ReadWriteInstanceSecretsManagerSecrets"
    Action = [
      "secretsmanager:GetSecretValue",
      "secretsmanager:DescribeSecret",
      "secretsmanager:GetResourcePolicy",
      "secretsmanager:ListSecretVersionIds",
      "secretsmanager:PutSecretValue",
      "secretsmanager:DeleteSecret",
    ]
    Effect = "Allow"
    Resource = [
      "${local.secret_arn_prefix}*" # wildcard to match all secrets corresponding to this function
    ]
  }] : []

  global_ssm_param_statements = length(var.global_parameter_arns) == 0 ? [] : [{
    Sid = "ReadSharedSSMParameters"
    Action = [
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParameterVersion",
      "ssm:GetParametersByPath",
    ]
    Effect   = "Allow"
    Resource = var.global_parameter_arns
  }]

  global_secretsmanager_statements = length(var.global_secrets_manager_secret_arns) == 0 ? [] : [{
    Sid = "ReadSharedSecrets"
    Action = [
      "secretsmanager:GetSecretValue",
      "secretsmanager:DescribeSecret",
      "secretsmanager:GetResourcePolicy",
      "secretsmanager:ListSecretVersionIds"
    ]
    Effect   = "Allow"
    Resource = values(var.global_secrets_manager_secret_arns)
  }]

  key_statements = length(local.kms_key_ids_to_allow) > 0 ? [{
    Sid = "AllowKMSUse"
    Action = [
      "kms:Decrypt",
      "kms:Encrypt", # needed, bc lambdas need to write some SSM parameters
    ]
    Effect   = "Allow"
    Resource = local.kms_keys_to_allow_arns
  }] : []

  s3_side_output_statements = var.side_output == null ? [] : [{
    Sid = "AllowS3SideOutput"
    Action = [
      "s3:PutObject",
      "s3:GetObject",
      "s3:DeleteObject",
      "s3:ListBucket"
    ]
    Effect   = "Allow"
    Resource = [
      "arn:aws:s3:::${var.side_output.bucket}",
      "arn:aws:s3:::${var.side_output.bucket}/*"
    ]
  }]

  policy_statements = concat(
    local.global_ssm_param_statements,
    local.global_secretsmanager_statements,
    local.local_ssm_param_statements,
    local.local_secrets_manager_statements,
    local.key_statements,
    local.s3_side_output_statements
  )
}

# what the lambda function needs to operate (fulfill its use-case)
resource "aws_iam_policy" "required_resource_access" {
  name = "${local.function_name}_resourceAccess"

  description = "Allow access to AWS resources needed by ${local.function_name}; specific resources depend on the use-case of the instance"

  policy = jsonencode(
    {
      "Version" : "2012-10-17",
      "Statement" : local.policy_statements
    }
  )

  lifecycle {
    ignore_changes = [
      name, # drop this in v0.6.x; name as of 0.5.2 was "${local.function_name}_ssmParameters"; but it's potentially broader than that
      description, # drop this in v0.6.x; description as of 0.5.2 was "Allow infra access parameter access needed by ${local.function_name}"
      tags
    ]
  }
}

resource "aws_iam_role_policy_attachment" "attach_policy" {
  role       = aws_iam_role.iam_for_lambda.name
  policy_arn = aws_iam_policy.required_resource_access.arn
}

output "function_arn" {
  value = aws_lambda_function.instance.arn
}

output "function_name" {
  value = local.function_name
}

output "iam_role_for_lambda_arn" {
  value = aws_iam_role.iam_for_lambda.arn
}

output "iam_role_for_lambda_name" {
  value = aws_iam_role.iam_for_lambda.name
}

output "log_group" {
  value = aws_cloudwatch_log_group.lambda_log.name
}
