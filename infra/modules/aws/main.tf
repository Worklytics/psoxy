# setup AWS project to host Psoxy instances

terraform {
  required_providers {
    aws = {
      version = ">= 4.12, < 5.0"
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
  count = var.use_api_gateway_v2 ? 0 : 1

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

resource "aws_iam_role_policy_attachment" "invoker_url_lambda_execution" {
  count = var.use_api_gateway_v2 ? 0 : 1

  role       = aws_iam_role.api-caller.name
  policy_arn = aws_iam_policy.execution_lambda_to_caller[0].arn
}


# not really a 'password', but 'random_string' isn't "sensitive" by terraform, so
# is output to console
resource "random_password" "pseudonym_salt" {
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

resource "aws_apigatewayv2_api" "proxy_api" {
  count = var.use_api_gateway_v2 ? 1 : 0

  name          = "${var.deployment_id}-api"
  protocol_type = "HTTP"
  description   = "API to expose ${var.deployment_id} psoxy instances"
}

# must have a stage deployed
resource "aws_apigatewayv2_stage" "live" {
  count = var.use_api_gateway_v2 ? 1 : 0

  api_id      = aws_apigatewayv2_api.proxy_api[0].id
  name        = "live" # q: what name?
  auto_deploy = true
  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.api_gatewayv2_log[0].arn
    format          = "$context.identity.sourceIp $context.identity.caller $context.identity.user [$context.requestTime] \"$context.httpMethod $context.path $context.protocol\" $context.status $context.responseLength $context.requestId $context.extendedRequestId $context.error.messageString $context.integrationErrorMessage"
  }
}

# TODO: it would maximize granularity of policy to push this into `aws-psoxy-rest` module, and
# do the statements based on configured list of http methods; but cost of that is policy + attachment
# for each instance, instead of one per deployment
resource "aws_iam_policy" "invoke_api" {
  count = var.use_api_gateway_v2 ? 1 : 0

  name_prefix = "${var.deployment_id}InvokeAPI"

  policy = jsonencode({
    "Version" : "2012-10-17",
    "Statement" : [
      {
        "Effect" : "Allow",
        "Action" : "execute-api:Invoke",
        "Resource" : "arn:aws:execute-api:*:${var.aws_account_id}:${aws_apigatewayv2_api.proxy_api[0].id}/*/GET/*",
      },
      {
        "Effect" : "Allow",
        "Action" : "execute-api:Invoke",
        "Resource" : "arn:aws:execute-api:*:${var.aws_account_id}:${aws_apigatewayv2_api.proxy_api[0].id}/*/HEAD/*",
      },
      {
        "Effect" : "Allow",
        "Action" : "execute-api:Invoke",
        "Resource" : "arn:aws:execute-api:*:${var.aws_account_id}:${aws_apigatewayv2_api.proxy_api[0].id}/*/POST/*",
      },
    ]
  })
}

resource "aws_iam_role_policy_attachment" "invoke_api_policy_to_role" {
  count = var.use_api_gateway_v2 ? 1 : 0

  role       = aws_iam_role.api-caller.name
  policy_arn = aws_iam_policy.invoke_api[0].arn
}

resource "aws_cloudwatch_log_group" "api_gatewayv2_log" {
  count = var.use_api_gateway_v2 ? 1 : 0

  name              = aws_apigatewayv2_api.proxy_api[0].name
  retention_in_days = 7
  kms_key_id        = var.logs_kms_key_arn
}

# install test tool, if it exists in expected location
module "test_tool" {
  count = var.install_test_tool ? 1 : 0

  source = "../psoxy-test-tool"

  path_to_tools = "${var.psoxy_base_dir}tools"
  # TODO: take version from somewhere else here; this isn't *necessary* the version if local build or remote artifact
  psoxy_version = module.psoxy_package.version
}

output "secrets" {
  value = {
    PSOXY_ENCRYPTION_KEY = {
      value               = sensitive(random_password.encryption_key.result),
      description         = "secret used to generate reversible pseudonyms, if any; rotate to render all existing ones irreversible"
      sensitive           = true
      value_managed_by_tf = true
    },
    PSOXY_SALT = {
      value               = sensitive(random_password.pseudonym_salt.result),
      description         = "Salt used to build pseudonyms."
      sensitive           = true
      value_managed_by_tf = true
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

output "pseudonym_salt" {
  description = "Value used to salt pseudonyms (SHA-256) hashes. If migrate to new deployment, you should copy this value."
  value       = random_password.pseudonym_salt.result
  sensitive   = true
}

output "api_gateway_v2" {
  # NOTE: filled based on `var.use_api_gateway_v2`, which is sufficient for Terraform to understand
  # pre-apply that it's going to have a non-null value
  value = var.use_api_gateway_v2 ? merge(
    {
      stage_invoke_url = aws_apigatewayv2_stage.live[0].invoke_url
    },
    aws_apigatewayv2_api.proxy_api[0]
  ) : null
}
