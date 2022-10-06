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

# q: makes policy dynamic, so actual statements don't appear in `terraform plan`?
# TODO: param_arn_prefix without relying something that itself is provisioned by terraform ...
data "aws_arn" "lambda" {
  arn = aws_lambda_function.psoxy-instance.arn
}

# q: creates implicit dependency on parameters being created, which may not be case in first run??

locals {
  prefix = "PSOXY_${upper(replace(var.source_kind, "-", "_"))}_"

  param_arn_prefix = "arn:aws:ssm:${data.aws_arn.lambda.region}:${data.aws_arn.lambda.account}:parameter/${local.prefix}"

  function_write_arns = [
    "${local.param_arn_prefix}*" # wildcard to match all params corresponding to this function
  ],
  function_read_arns  = concat(
    [
      "${local.param_arn_prefix}*" # wildcard to match all params corresponding to this function
    ],
    var.global_parameter_arns
  )

  write_statements =  [{
    Action   = [
      "ssm:PutParameter"
    ]
    Effect   = "Allow"
    Resource = local.function_write_arns
  }]

  read_statements = [{
    Action = [
      "ssm:GetParameter*"
    ]
    Effect   = "Allow"
    Resource =  local.function_read_arns
  }]

  policy_statements = concat(
    local.read_statements,
    local.write_statements
  )
}

resource "aws_iam_policy" "ssm_param_policy" {
  name        = "${var.function_name}_ssmParameters"
  description = "Allow SSM parameter access needed by ${var.function_name}"

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


resource "aws_iam_role_policy_attachment" "basic" {
  role       = aws_iam_role.iam_for_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy_attachment" "attach_policy" {
  role       = aws_iam_role.iam_for_lambda.name
  policy_arn = aws_iam_policy.ssm_param_policy.arn
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
