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
  source = "../../modular-examples/aws-msft-365"
  # source = "git::https://github.com/worklytics/psoxy//infra/modular-examples/aws-msft-365?ref=v0.4.13"

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
  msft_owners_email              = var.msft_owners_email
  pseudonymize_app_ids           = var.pseudonymize_app_ids
  general_environment_variables  = var.general_environment_variables
  #  aws_ssm_key_id                 = aws_kms_key.key.key_id
}

#resource "aws_kms_key" "key"  {
#}

# if you generated these, you may want them to import back into your data warehouse
output "lookup_tables" {
  value = module.psoxy-aws-msft-365.lookup_tables
}

output "todos_1" {
  description = "List of todo steps to complete 1st, in markdown format."
  value       = join("\n", module.psoxy-aws-msft-365.todos_1)
}

output "todos_2" {
  description = "List of todo steps to complete 2nd, in markdown format."
  value       = join("\n", module.psoxy-aws-msft-365.todos_2)
}

output "todos_3" {
  description = "List of todo steps to complete 3rd, in markdown format."
  value       = join("\n", module.psoxy-aws-msft-365.todos_3)
}

moved {
  from = module.worklytics_connector_specs
  to   = module.psoxy-aws-msft-365.module.worklytics_connector_specs
}

moved {
  from = module.psoxy-aws
  to   = module.psoxy-aws-msft-365.module.psoxy-aws
}

moved {
  from = module.global_secrets
  to   = module.psoxy-aws-msft-365.module.global_secrets
}

moved {
  from = module.msft-connection
  to   = module.psoxy-aws-msft-365.module.msft-connection
}

moved {
  from = module.msft-connection-auth
  to   = module.psoxy-aws-msft-365.module.msft-connection-auth
}

moved {
  from = module.private-key-aws-parameters
  to   = module.psoxy-aws-msft-365.module.msft-365-connector-key-secrets
}

moved {
  from = module.psoxy-msft-connector
  to   = module.psoxy-aws-msft-365.module.psoxy-msft-connector
}

moved {
  from = module.worklytics-psoxy-connection-msft-365
  to   = module.psoxy-aws-msft-365.module.worklytics-psoxy-connection-msft-365
}

moved {
  from = aws_ssm_parameter.long-access-secrets
  to   = module.psoxy-aws-msft-365.aws_ssm_parameter.long-access-secrets
}

moved {
  from = module.parameter-fill-instructions
  to   = module.psoxy-aws-msft-365.module.parameter-fill-instructions
}

moved {
  from = module.source_token_external_todo
  to   = module.psoxy-aws-msft-365.module.source_token_external_todo
}

moved {
  from = module.aws-psoxy-long-auth-connectors
  to   = module.psoxy-aws-msft-365.module.aws-psoxy-long-auth-connectors
}

moved {
  from = module.worklytics-psoxy-connection-oauth-long-access
  to   = module.psoxy-aws-msft-365.module.worklytics-psoxy-connection-oauth-long-access
}

moved {
  from = module.psoxy-bulk
  to   = module.psoxy-aws-msft-365.module.psoxy-bulk
}
