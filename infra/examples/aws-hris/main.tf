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
  source = "git::https://github.com/worklytics/psoxy//infra/modules/aws?ref=v0.3.0-beta.1"

  caller_aws_account_id   = var.caller_aws_account_id
  caller_external_user_id = var.caller_external_user_id
  aws_account_id          = var.aws_account_id
  path_to_psoxy_java      = "../../../java"
}

module "psoxy-package" {
  source = "git::https://github.com/worklytics/psoxy//infra/modules/psoxy-package?ref=v0.3.0-beta.1"

  implementation     = "aws"
}

module "psoxy-hris" {
  source = "../../modules/aws-bulk"

  caller_aws_account_id   = var.caller_aws_account_id
  caller_external_user_id = var.caller_external_user_id
  aws_account_id          = var.aws_account_id
  aws_assume_role_arn     = var.aws_assume_role_arn
  instance_id             = var.instance_id
  source_kind             = var.source_kind
  aws_region              = var.aws_region
  path_to_function_zip    = module.psoxy-package.path_to_deployment_jar
  function_zip_hash       = module.psoxy-package.deployment_package_hash
  path_to_config          = "../../../configs/hris.yaml"
  api_caller_role_arn     = module.psoxy-aws.api_caller_role_arn
  api_caller_role_name    = module.psoxy-aws.api_caller_role_name
}
