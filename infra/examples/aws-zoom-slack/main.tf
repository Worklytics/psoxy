terraform {
  required_providers {
    # for the infra that will host Psoxy instances
    aws = {
      source  = "hashicorp/aws"
      version = "~> 4.12"
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
  source = "git::https://github.com/worklytics/psoxy//infra/modules/aws?ref=v0.3.0-beta.4"

  caller_aws_account_id   = var.caller_aws_account_id
  caller_external_user_id = var.caller_external_user_id
  aws_account_id          = var.aws_account_id

  providers = {
    aws = aws
  }
}

module "psoxy-package" {
  source = "git::https://github.com/worklytics/psoxy//infra/modules/psoxy-package?ref=v0.3.0-beta.4"

  implementation     = "aws"
  path_to_psoxy_java = "${var.psoxy_base_dir}/java"
}

# BEGIN LONG ACCESS AUTH CONNECTORS

locals {
  oauth_long_access_connectors = {
    slack-discovery-api = {
      enabled : true
      source_kind : "slack"
      display_name : "Slack Discovery API"
      example_api_calls : []
    },
    zoom = {
      enabled : true
      source_kind : "zoom"
      display_name : "Zoom"
      example_api_calls : ["/v2/users"]
    }
  }
  enabled_oauth_long_access_connectors = { for k, v in local.oauth_long_access_connectors : k => v if v.enabled }
  base_config_path                     = "${var.psoxy_base_dir}/configs/"
}

# Create secret (later filled by customer)
resource "aws_ssm_parameter" "long-access-token-secret" {
  for_each = local.enabled_oauth_long_access_connectors

  name        = "PSOXY_${upper(replace(each.key, "-", "_"))}_ACCESS_TOKEN"
  type        = "SecureString"
  description = "The long lived token for `psoxy-${each.key}`"
  value       = sensitive("TODO: fill me with a real token!! (via AWS console)")

  lifecycle {
    ignore_changes = [
      value # we expect this to be filled via Console, so don't want to overwrite it with the dummy value if changed
    ]
  }
}

module "aws-psoxy-long-auth-connectors" {
  for_each = local.enabled_oauth_long_access_connectors

  source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-instance?ref=v0.3.0-beta.4"

  function_name        = "psoxy-${each.key}"
  source_kind          = each.value.source_kind
  path_to_function_zip = module.psoxy-package.path_to_deployment_jar
  function_zip_hash    = module.psoxy-package.deployment_package_hash
  path_to_config       = "${local.base_config_path}/${each.value.source_kind}.yaml"
  aws_assume_role_arn  = var.aws_assume_role_arn
  aws_account_id       = var.aws_account_id

  parameters = [
    module.psoxy-aws.salt_secret,
    aws_ssm_parameter.long-access-token-secret[each.key]
  ]
  example_api_calls = each.value.example_api_calls
}

module "worklytics-psoxy-connection" {
  for_each = local.enabled_oauth_long_access_connectors

  source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection-aws?ref=v0.3.0-beta.4"

  psoxy_endpoint_url = module.aws-psoxy-long-auth-connectors[each.key].endpoint_url
  display_name       = "${each.value.display_name} via Psoxy${var.connector_display_name_suffix}"
  aws_region         = var.aws_region
  aws_role_arn       = module.psoxy-aws.api_caller_role_arn
}

# END LONG ACCESS AUTH CONNECTORS
