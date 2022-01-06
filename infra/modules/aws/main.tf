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

# role that Worklytics user will use to call the API
resource "aws_iam_role" "api-caller" {
  name = "PsoxyApiCaller"

  assume_role_policy = jsonencode({
    "Version" : "2012-10-17",
    "Statement" : [
      {
        "Effect" : "Allow"
        "Resource" : [
          "${aws_apigatewayv2_api.psoxy-api.arn}/*/*/*"
        ]
        "Principal" : {
          "AWS" : "arn:aws:iam::${var.caller_aws_account_id}"
        },
        "Condition" : {
          "StringEquals" : {
            "sts:ExternalId" : var.caller_aws_user_id
          }
        }
      }
    ]
  })

  inline_policy {
    name = "invoke"
    policy = jsonencode({
      "Version" : "2012-10-17",
      "Statement" : [
        {
          "Effect" : "Allow"
          "Action" : "execute-api:Invoke"
          "Resource" : [
            "${aws_apigatewayv2_api.psoxy-api.arn}/*/*/*"
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
resource "aws_secretsmanager_secret_version" "initial_version" {
  secret_id     = aws_secretsmanager_secret.pseudonymization-salt.id
  secret_string = sensitive(random_password.random.result)
}

output "salt_secret_id" {
  value = aws_secretsmanager_secret.pseudonymization-salt.id
}

output "salt_secret_version_id" {
  value = aws_secretsmanager_secret_version.initial_version.version_id
}

output "api_gateway" {
  value = aws_apigatewayv2_api.psoxy-api
}
