terraform {
  required_providers {
    # for the infra that will host Psoxy instances
    aws = {
      source  = "hashicorp/aws"
      version = "~> 4.12"
    }

    # for API connections to Microsoft 365
    azuread = {
      version = "~> 2.3"
    }
  }

  # we recommend you use a secure location for your Terraform state (such as S3 bucket), as it
  # may contain sensitive values (such as API keys) depending on which data sources you configure.
  #
  # local may be safe for production-use IFF you are executing Terraform from a secure location
  #
  # Please review and seek guidance from your Security team if in doubt.
  backend "local" {
  }

  # example remove backend (this S3 bucket must already be provisioned, and AWS role executing
  # terraform must be able to read/write to it - and use encryption key, if any)
  #  backend "s3" {
  #    bucket = "mybucket"
  #    key    = "path/to/my/key"
  #    region = "us-east-1"
  #  }
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

provider "azuread" {
  tenant_id = var.msft_tenant_id
}

module "psoxy-aws-msft-365" {
  # source = "../../modular-examples/aws-msft-365"
  source = "git::https://github.com/worklytics/psoxy//infra/modular-examples/aws-msft-365?ref=v0.4.11"

  aws_account_id                 = var.aws_account_id
  aws_assume_role_arn            = var.aws_assume_role_arn # role that can test the instances (lambdas)
  aws_region                     = var.aws_region
  aws_ssm_param_root_path        = var.aws_ssm_param_root_path
  psoxy_base_dir                 = var.psoxy_base_dir
  force_bundle                   = var.force_bundle
  caller_gcp_service_account_ids = var.caller_gcp_service_account_ids
  caller_aws_arns                = var.caller_aws_arns
  enabled_connectors             = var.enabled_connectors
  non_production_connectors      = var.non_production_connectors
  connector_display_name_suffix  = var.connector_display_name_suffix
  custom_bulk_connectors         = var.custom_bulk_connectors
  lookup_table_builders          = var.lookup_table_builders
  msft_tenant_id                 = var.msft_tenant_id
  pseudonymize_app_ids           = var.pseudonymize_app_ids
  general_environment_variables  = var.general_environment_variables
}

# if you generated these, you may want them to import back into your data warehouse
output "lookup_tables" {
  value = module.psoxy-aws-msft-365.lookup_tables
}
