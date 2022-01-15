# setup AWS project to host Psoxy instances

terraform {
  required_providers {
    aws = {
      version = "~> 3.0"
    }
  }
}

# AWS API Gateway
resource "aws_apigatewayv2_api" "psoxy-api" {
  name          = "psoxy-api"
  protocol_type = "HTTP"
  description   = "API to expose psoxy instances"
}

resource "aws_cloudwatch_log_group" "gateway-log" {
  name = aws_apigatewayv2_api.psoxy-api.name
}

resource "aws_apigatewayv2_stage" "live" {
  api_id        = aws_apigatewayv2_api.psoxy-api.id
  name          = "live" # q: what name??
  auto_deploy   = true
  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.gateway-log.arn
    format          = "$context.identity.sourceIp $context.identity.caller $context.identity.user [$context.requestTime] \"$context.httpMethod $context.resourcePath $context.protocol\" $context.status $context.responseLength $context.requestId $context.extendedRequestId"
  }
}

# role that Worklytics user will use to call the API
resource "aws_iam_role" "api-caller" {
  name = "PsoxyApiCaller"

  # who can assume this role
  assume_role_policy = jsonencode({
    "Version" : "2012-10-17",
    "Statement" : [
      {
        "Action" = "sts:AssumeRole"
        "Effect" : "Allow"
        "Principal" : {
          "AWS" : "arn:aws:iam::${var.caller_aws_account_id}"
        }
        #TODO: add condition referencing GCP service account that will auth with Worklytics AWS account to call
        #"Condition" : {
        #  "StringEquals" : {
        #    "sts:ExternalId" : var.caller_aws_user_id
        #  }
        #}
      },
      # https://docs.aws.amazon.com/apigateway/latest/developerguide/permissions.html
      {
        "Sid": "",
        "Effect": "Allow",
        "Principal": {
          "Service": "apigateway.amazonaws.com"
        },
        "Action": "sts:AssumeRole"
      }
    ]
  })

  # what this role can do (invoke anything in the API gateway )
  inline_policy {
    name = "lambda-invoker"
    policy = jsonencode({
      "Version" : "2012-10-17",
      "Statement" : [
        {
          "Effect": "Allow",
          "Action": "execute-api:Invoke",
          # "Resource": "arn:aws:execute-api:*:${var.aws_account_id}:*/*/GET/*",
          "Resource": "arn:aws:execute-api:*:${var.aws_account_id}:*",
        }
      ]
    })
  }
  managed_policy_arns = [
    # I think this was created when creating the role from
    # https://docs.aws.amazon.com/apigateway/latest/developerguide/permissions.html
    "arn:aws:iam::aws:policy/service-role/AmazonAPIGatewayPushToCloudWatchLogs"
  ]
  # not sure about this one
  inline_policy {
    name = "read-gateway"
    policy = jsonencode({
      "Version": "2012-10-17",
      "Statement": [
        {
          "Effect": "Allow",
          "Action": [
            "apigateway:GET"
          ],
          "Resource": [
            "arn:aws:apigateway:us-east-1::/apis/${var.aws_account_id}/*"
          ]
        }
      ]
    })
  }
  inline_policy {
    name = "sns-list-topics"
    policy = jsonencode({
      "Version": "2012-10-17",
      "Statement": [
        {
          "Effect": "Allow",
          "Resource": [
            "*"
          ],
          "Action": [
            "sns:ListTopics"
          ]
        }
      ]
    })
  }

}

# pseudo secret
resource "aws_secretsmanager_secret" "pseudonymization-salt" {
  name = "PSOXY_SALT"
}

# not really a 'password', but 'random_string' isn't "sensitive" by terraform, so
# is output to console
resource "random_password" "random" {
  length           = 20
  special          = true
}

# initial random salt to use; if you DON'T want this in your Terraform state, create a new version
# via some other means (eg, directly in GCP console). this should be done BEFORE your psoxy
# instance pseudonymizes anything; if salt is changed later, pseudonymization output will differ so
# previously pseudonymized data will be inconsistent with data pseudonymized after the change.
#
# To be clear, possession of salt alone doesn't let someone reverse pseudonyms.
resource "aws_ssm_parameter" "salt" {
  name        = "PSOXY_SALT"
  type        = "SecureString"
  description = "Salt used to build pseudonyms"
  value       = sensitive(random_password.random.result)
}

output "salt_secret" {
  value = aws_ssm_parameter.salt
}

output "api_gateway" {
  value = aws_apigatewayv2_api.psoxy-api
}

output "api_caller_role_arn" {
  value = aws_iam_role.api-caller.arn
}
