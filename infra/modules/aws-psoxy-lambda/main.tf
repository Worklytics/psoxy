# provision a Psoxy instance into AWS account

terraform {
  required_providers {
    aws = {
      version = "~> 4.12"
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


  # parse PATH_TO_SHARED_CONFIG in super-hacky way
  # expect something like:
  # arn:aws:ssm:us-east-1:123123123123:parameter/PSOXY_SALT
  salt_arn              = [for l in var.global_parameter_arns : l if endswith(l, local.salt_parameter_name_suffix)][0]
  path_to_shared_config = regex("arn.+parameter(/.*)${local.salt_parameter_name_suffix}", local.salt_arn)[0]

  bundle_from_s3 = startswith(var.path_to_function_zip, "s3://")
  s3_bucket      = local.bundle_from_s3 ? regex("s3://([^/]+)/.*", var.path_to_function_zip)[0] : null
  s3_key         = local.bundle_from_s3 ? regex("s3://[^/]+/(.*)", var.path_to_function_zip)[0] : null
  bundle_filename = try(regex(".*/([^/]+)", var.path_to_function_zip)[0], var.path_to_function_zip)
}


resource "aws_lambda_function" "instance" {
  function_name                  = local.function_name
  role                           = aws_iam_role.iam_for_lambda.arn
  architectures                  = ["arm64"] # 20% cheaper per ms exec time than x86_64
  runtime                        = "java11"
  filename                       = local.bundle_from_s3 ? null : var.path_to_function_zip
  s3_bucket                      = local.s3_bucket # null if local file
  s3_key                         = local.s3_key    # null if local file
  source_code_hash               = var.function_zip_hash
  handler                        = var.handler_class
  timeout                        = var.timeout_seconds
  memory_size                    = var.memory_size_mb
  reserved_concurrent_executions = coalesce(var.reserved_concurrent_executions, -1)
  kms_key_arn                    = var.function_env_kms_key_arn

  environment {
    variables = merge(
      var.path_to_config == null ? {} : yamldecode(file(var.path_to_config)),
      var.environment_variables,
      {
        EXECUTION_ROLE  = aws_iam_role.iam_for_lambda.arn,
        BUNDLE_FILENAME = local.bundle_filename
      },
      # only set env vars for config paths if non-default values
      length(local.path_to_shared_config) > 1 ? { PATH_TO_SHARED_CONFIG = local.path_to_shared_config } : {},
      local.is_instance_ssm_prefix_default ? {} : { PATH_TO_INSTANCE_CONFIG = var.path_to_instance_ssm_parameters }
    )
  }

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

moved {
  from = aws_lambda_function.psoxy-instance
  to   = aws_lambda_function.instance
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

moved {
  from = aws_cloudwatch_log_group.lambda-log
  to   = aws_cloudwatch_log_group.lambda_log
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

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

resource "aws_iam_role_policy_attachment" "basic" {
  role       = aws_iam_role.iam_for_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
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

  global_ssm_param_statements = [{
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

  key_statements = length(local.kms_key_ids_to_allow) > 0 ? [{
    Sid = "AllowKMSUse"
    Action = [
      "kms:Decrypt",
      "kms:Encrypt", # needed, bc lambdas need to write some SSM parameters
    ]
    Effect   = "Allow"
    Resource = local.kms_keys_to_allow_arns
  }] : []

  policy_statements = concat(
    local.global_ssm_param_statements,
    local.local_ssm_param_statements,
    local.key_statements
  )
}

resource "aws_iam_policy" "ssm_param_policy" {
  name        = "${local.function_name}_ssmParameters"
  description = "Allow SSM parameter access needed by ${local.function_name}"

  policy = jsonencode(
    {
      "Version" : "2012-10-17",
      "Statement" : local.policy_statements
    }
  )

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

resource "aws_iam_role_policy_attachment" "attach_policy" {
  role       = aws_iam_role.iam_for_lambda.name
  policy_arn = aws_iam_policy.ssm_param_policy.arn
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
