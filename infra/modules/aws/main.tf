# setup AWS project to host Psoxy instances

terraform {
  required_providers {
    aws = {
      version = "~> 4.12"
    }
  }
}


locals {

  aws_caller_statements = [
    for arn in var.caller_aws_arns :
    {
      Action : "sts:AssumeRole"
      Effect : "Allow"
      Principal : {
        "AWS" : arn
      }
    }
  ]

  gcp_service_account_caller_statements = [
    for id in var.caller_gcp_service_account_ids :
    {
      Action : "sts:AssumeRoleWithWebIdentity",
      Effect : "Allow",
      Principal : {
        "Federated" : "accounts.google.com"
      },
      Condition : {
        "StringEquals" : {
          "accounts.google.com:aud" : id
        }
      }
    }
  ]
}

# NOTE: region used to be passed in as a variable; put it MUST match the region in which the lambda
# is provisioned, and that's implicit in the provider - so we should just infer from the provider
data "aws_region" "current" {}


# role that Worklytics user will use to call the API
resource "aws_iam_role" "api-caller" {
  name        = "${var.deployment_id}Caller"
  description = "role for AWS principals that may invoke the psoxy instance or read an instance's output"

  # who can assume this role
  assume_role_policy = jsonencode({
    "Version" : "2012-10-17",
    "Statement" : concat(
      [
        {
          Sid : "" # default value; if omit, Terraform seems change back to `null` in subsequent applies
          Action : "sts:AssumeRole",
          Effect : "Allow",
          Principal : {
            "Service" : "lambda.amazonaws.com"
          },
        },
      ],
      local.aws_caller_statements,
      local.gcp_service_account_caller_statements
    )
  })

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

locals {
  function_name_prefix = coalesce(var.rest_function_name_prefix, var.api_function_name_prefix)
}

resource "aws_iam_policy" "execution_lambda_to_caller" {
  name        = "${var.deployment_id}ExecuteLambdas"
  description = "Allow caller role to execute the lambda url directly"

  policy = jsonencode(
    {
      "Version" : "2012-10-17",
      "Statement" : [
        {
          "Action" : ["lambda:InvokeFunctionUrl"],
          "Effect" : "Allow",
          "Resource" : "arn:aws:lambda:${data.aws_region.current.id}:${var.aws_account_id}:function:${local.function_name_prefix}*"
        }
      ]
  })

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

resource "aws_iam_role_policy_attachment" "invoker_lambda_execution" {
  role       = aws_iam_role.api-caller.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy_attachment" "invoker_url_lambda_execution" {
  role       = aws_iam_role.api-caller.name
  policy_arn = aws_iam_policy.execution_lambda_to_caller.arn
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


# not really a 'password', but 'random_string' isn't "sensitive" by terraform, so
# is output to console
resource "random_password" "encryption_key" {
  length  = 32 //256-bits
  special = true
}

module "psoxy_package" {
  source = "../psoxy-package"

  implementation     = "aws"
  path_to_psoxy_java = "${var.psoxy_base_dir}java"
  deployment_bundle  = var.deployment_bundle
  psoxy_version      = var.psoxy_version
  force_bundle       = var.force_bundle
}

moved {
  from = module.psoxy-package
  to   = module.psoxy_package
}

# install test tool, if it exists in expected location
module "test_tool" {
  count = var.install_test_tool ? 1 : 0

  source = "../psoxy-test-tool"

  path_to_tools = "${var.psoxy_base_dir}tools"
  psoxy_version = module.psoxy_package.version
}

moved {
  from = module.test_tool
  to   = module.test_tool[0]
}

output "secrets" {
  value = {
    PSOXY_ENCRYPTION_KEY = {
      value       = sensitive(random_password.encryption_key.result),
      description = "secret used to generate reversible pseudonyms, if any; rotate to render all existing ones irreversible"
    },
    PSOXY_SALT = {
      value       = sensitive(random_password.random.result),
      description = "Salt used to build pseudonyms."
    }
  }
}

output "api_caller_role_arn" {
  value = aws_iam_role.api-caller.arn
}

output "api_caller_role_name" {
  value = aws_iam_role.api-caller.name
}

output "deployment_package_hash" {
  value = module.psoxy_package.deployment_package_hash
}

output "path_to_deployment_jar" {
  value = module.psoxy_package.path_to_deployment_jar
}

output "filename" {
  value = module.psoxy_package.filename
}
