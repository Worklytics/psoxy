terraform {
  required_providers {
    # for the infra that will host Psoxy instances
    aws = {
      source  = "hashicorp/aws"
      version = "~> 3.0"
    }

    # for the API connections to Google Workspace
    google = {
      version = ">= 3.74, <= 4.0"
    }
  }

  # if you leave this as local, you should backup/commit your TF state files
  backend "local" {
  }
}

# NOTE: you need to provide credentials. usual way to do this is to set env vars:
#        AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
# see https://registry.terraform.io/providers/hashicorp/aws/latest/docs#authentication for more
# information as well as alternative auth approaches
provider "aws" {
  region = var.aws_region

  assume_role {
    role_arn = var.aws_assume_role_arn
  }
  allowed_account_ids = [
    var.aws_account_id
  ]
}

module "psoxy-aws" {
  source = "../../modules/aws"

  caller_aws_account_id   = var.caller_aws_account_id
  caller_external_user_id = var.caller_external_user_id
  aws_account_id          = var.aws_account_id

  providers = {
    aws = aws
  }
}

module "psoxy-package" {
  source = "../../modules/psoxy-package"

  implementation     = "aws"
  path_to_psoxy_java = "../../../java"
}

# BEGIN LONG ACCESS AUTH CONNECTORS

locals {
  oauth_long_access_connectors = {
    slack = {
      deploy = true
      function_name = "psoxy-slack-discovery-api"
      source_kind = "slack"
      example_api_calls = []
    },
    zoom = {
      deploy = true
      function_name = "psoxy-zoom"
      source_kind = "zoom"
      example_api_calls = []
    }
  }
}

# Create secret (later filled by customer)
resource "aws_ssm_parameter" "long-access-token-secret" {
  for_each = {
  for k, v in local.oauth_long_access_connectors:
  k => v if v.deploy
  }
  name        = "${each.value.function_name}ACCESS_TOKEN"
  type        = "SecureString"
  description = "Long-lived access token for the connector"
  value       = sensitive("")
}

module "aws-psoxy-long-auth-connectors" {
  for_each = {
  for k, v in local.oauth_long_access_connectors:
  k => v if v.deploy
  }

  source = "../../modules/aws-psoxy-instance"

  function_name        = each.value.function_name
  source_kind          = each.value.source_kind
  api_gateway          = module.psoxy-aws.api_gateway
  path_to_function_zip = module.psoxy-package.path_to_deployment_jar
  function_zip_hash    = module.psoxy-package.deployment_package_hash
  path_to_config       = "../../../configs/${each.key}.yaml"
  api_caller_role_arn  = module.psoxy-aws.api_caller_role_arn
  aws_assume_role_arn  = var.aws_assume_role_arn

  parameters   = [
    module.psoxy-aws.salt_secret,
    aws_ssm_parameter.long-access-token-secret[each.key]
  ]
  example_api_calls = each.value.example_api_calls
}

# END LONG ACCESS AUTH CONNECTORS
