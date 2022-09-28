# provision a Psoxy instance into AWS account

terraform {
  required_providers {
    aws = {
      version = "~> 4.12"
    }
  }
}

resource "aws_lambda_function" "psoxy-instance" {
  function_name                  = var.function_name
  role                           = aws_iam_role.iam_for_lambda.arn
  architectures                  = ["arm64"] # 20% cheaper per ms exec time than x86_64
  runtime                        = "java11"
  filename                       = var.path_to_function_zip
  source_code_hash               = var.function_zip_hash
  handler                        = var.handler_class
  timeout                        = var.timeout_seconds
  memory_size                    = var.memory_size_mb
  reserved_concurrent_executions = coalesce(var.reserved_concurrent_executions, -1)

  environment {
    variables = merge(
      var.path_to_config == null ? {} : yamldecode(file(var.path_to_config)),
      var.environment_variables
    )
  }

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

# cloudwatch group per lambda function
resource "aws_cloudwatch_log_group" "lambda-log" {
  name              = "/aws/lambda/${aws_lambda_function.psoxy-instance.function_name}"
  retention_in_days = var.log_retention_in_days

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

resource "aws_iam_role" "iam_for_lambda" {
  name = "iam_for_lambda_${var.function_name}"

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

# "PSOXY_${upper(replace(each.value.connector_name, "-", "_"))}_${upper(each.value.secret_name)}"
data "aws_ssm_parameters_by_path" "psoxy_parameters" {
  path            = "/"
  with_decryption = false # we just want the arns, not the values
}

locals {
  prefix = "PSOXY_${upper(replace(var.source_kind, "-", "_"))}_"

  # Read grant to any param that belongs to the function (identified by param prefix)
  # Can't use same approach as for write, because some params are not defined in connector specs,
  # but later in certain modules, f.e. SERVICE_ACCOUNT_KEY or HRIS_RULES
  filtered_function_read_arns = [
    for arn in data.aws_ssm_parameters_by_path.psoxy_parameters.arns : arn if length(regexall(local.prefix, arn)) > 0
  ]

  # Write grant to any writeable param specified in the connector definition
  filtered_function_write_arns = distinct(flatten([
    for p in var.function_parameters : [
      for arn in data.aws_ssm_parameters_by_path.psoxy_parameters.arns :
      arn if endswith(arn, join("", [local.prefix, p.name])) && p.writable
    ]
  ]))

  function_write_arns = local.filtered_function_write_arns
  function_read_arns  = concat(local.filtered_function_read_arns, var.global_parameters)
}

resource "aws_iam_policy" "read_policy" {
  count       = length(local.function_read_arns) > 0 ? 1 : 0
  name        = "${var.function_name}_ssmGetParameters"
  description = "Allow lambda function role to read SSM parameters"

  policy = jsonencode(
    {
      "Version" : "2012-10-17",
      "Statement" : [
        {
          "Action" : [
            "ssm:GetParameter*"
          ],
          "Effect" : "Allow",
          "Resource" : local.function_read_arns
        }
      ]
  })

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

# policy fails if empty, so need to use count
resource "aws_iam_policy" "write_policy" {
  count       = length(local.function_write_arns) > 0 ? 1 : 0
  name        = "${var.function_name}_ssmPutParameters"
  description = "Allow lambda function role to update SSM parameters"

  policy = jsonencode(
    {
      "Version" : "2012-10-17",
      "Statement" : [
        {
          "Action" : [
            "ssm:PutParameter"
          ],
          "Effect" : "Allow",
          "Resource" : local.function_write_arns
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

resource "aws_iam_role_policy_attachment" "attach_read_policy" {
  count      = length(local.function_read_arns) > 0 ? 1 : 0
  role       = aws_iam_role.iam_for_lambda.name
  policy_arn = aws_iam_policy.read_policy[0].arn
}

resource "aws_iam_role_policy_attachment" "attach_write_policy" {
  count      = length(local.function_write_arns) > 0 ? 1 : 0
  role       = aws_iam_role.iam_for_lambda.name
  policy_arn = aws_iam_policy.write_policy[0].arn
}


output "function_arn" {
  value = aws_lambda_function.psoxy-instance.arn
}

output "function_name" {
  value = aws_lambda_function.psoxy-instance.function_name
}

output "iam_role_for_lambda_arn" {
  value = aws_iam_role.iam_for_lambda.arn
}

output "iam_role_for_lambda_name" {
  value = aws_iam_role.iam_for_lambda.name
}
