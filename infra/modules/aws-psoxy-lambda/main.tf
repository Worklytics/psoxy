# provision a Psoxy instance into AWS account

terraform {
  required_providers {
    aws = {
      version = "~> 4.12"
    }
  }
}

resource "aws_lambda_function" "psoxy-instance" {
  function_name    = var.function_name
  role             = aws_iam_role.iam_for_lambda.arn
  architectures    = ["arm64"] # 20% cheaper per ms exec time than x86_64
  runtime          = "java11"
  filename         = var.path_to_function_zip
  source_code_hash = var.function_zip_hash
  handler          = var.handler_class
  timeout          = var.timeout_seconds
  memory_size      = var.memory_size_mb

  environment {
    variables = merge(var.environment_variables, yamldecode(file(var.path_to_config)))
  }
}

# cloudwatch group per lambda function
resource "aws_cloudwatch_log_group" "lambda-log" {
  name              = "/aws/lambda/${aws_lambda_function.psoxy-instance.function_name}"
  retention_in_days = var.log_retention_in_days
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
}

resource "aws_iam_policy" "policy" {
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
          "Resource" : "*"
          # TODO: limit to SSM parameters in question
          # "Resource": "arn:aws:ssm:us-east-2:123456789012:parameter/prod-*"
        }
      ]
  })

}

resource "aws_iam_role_policy_attachment" "basic" {
  role       = aws_iam_role.iam_for_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy_attachment" "policy" {
  role       = aws_iam_role.iam_for_lambda.name
  policy_arn = aws_iam_policy.policy.arn
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
