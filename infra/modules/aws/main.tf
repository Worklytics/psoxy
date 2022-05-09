# setup AWS project to host Psoxy instances

terraform {
  required_providers {
    aws = {
      version = "~> 4.12"
    }
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
        "Action" : "sts:AssumeRole",
        "Principal" : {
          "Service" : "lambda.amazonaws.com"
        },
        "Effect" : "Allow",
        "Sid" : ""
      },
      {
        "Action" = "sts:AssumeRole"
        "Effect" : "Allow"
        "Principal" : {
          "AWS" : "arn:aws:iam::${var.caller_aws_account_id}"
        }
      },
      # allows service account to assume role
      {
        "Effect" : "Allow",
        "Principal" : {
          "Federated" : "accounts.google.com"
        },
        "Action" : "sts:AssumeRoleWithWebIdentity",
        "Condition" : {
          "StringEquals" : {
            "accounts.google.com:aud" : var.caller_external_user_id
          }
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "invoker_lambda_execution" {
  role       = aws_iam_role.api-caller.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}


# not really a 'password', but 'random_string' isn't "sensitive" by terraform, so
# is output to console
resource "random_password" "random" {
  length  = 20
  special = true
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


module "psoxy-package" {
  source = "../psoxy-package"

  implementation     = "aws"
}

output "salt_secret" {
  value = aws_ssm_parameter.salt
}

output "api_caller_role_arn" {
  value = aws_iam_role.api-caller.arn
}

output "api_caller_role_name" {
  value = aws_iam_role.api-caller.name
}

output "deployment_package_hash" {
  value = module.psoxy-package.deployment_package_hash
}

output "path_to_deployment_jar" {
  value = module.psoxy-package.path_to_deployment_jar
}

output "filename" {
  value = module.psoxy-package.filename
}
