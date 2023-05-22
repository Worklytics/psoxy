# example configuration of Psoxy deployment for Google Workspace-based organization into AWS

terraform {
  required_providers {
    # for the infra that will host Psoxy instances
    aws = {
      source  = "hashicorp/aws"
      version = "~> 4.15"
    }

    # for the API connections to Google Workspace
    google = {
      version = ">= 3.74, <= 5.0"
    }

    vault = {
      source  = "hashicorp/vault"
      version = "~> 3.11.0"
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

provider "google" {
  impersonate_service_account = var.gcp_terraform_sa_account_email
}

# Google user or service account which Terraform is authenticated as must be authorized to
# provision resources (Service Accounts + Keys; and activate APIs) in this project
data "google_project" "psoxy-google-connectors" {
  project_id = var.gcp_project_id
}

module "psoxy" {
  source = "../../modular-examples/aws-google-workspace"
  # source = "git::https://github.com/worklytics/psoxy//infra/modular-examples/aws-google-workspace?ref=v0.4.22"

  aws_account_id                 = var.aws_account_id
  aws_assume_role_arn            = var.aws_assume_role_arn # role that can test the instances (lambdas)
  aws_region                     = var.aws_region
  aws_ssm_param_root_path        = var.aws_ssm_param_root_path
  psoxy_base_dir                 = var.psoxy_base_dir
  force_bundle                   = var.force_bundle
  caller_aws_arns                = var.caller_aws_arns
  caller_gcp_service_account_ids = var.caller_gcp_service_account_ids
  environment_name               = var.environment_name
  connector_display_name_suffix  = var.connector_display_name_suffix
  enabled_connectors             = var.enabled_connectors
  non_production_connectors      = var.non_production_connectors
  custom_rest_rules              = var.custom_rest_rules
  custom_bulk_connectors         = var.custom_bulk_connectors
  lookup_table_builders          = var.lookup_table_builders
  gcp_project_id                 = data.google_project.psoxy-google-connectors.project_id
  google_workspace_example_user  = var.google_workspace_example_user
  google_workspace_example_admin = var.google_workspace_example_admin
  general_environment_variables  = var.general_environment_variables
  pseudonymize_app_ids           = var.pseudonymize_app_ids
  salesforce_domain              = var.salesforce_domain
  jira_server_url = var.jira_server_url
  jira_cloud_id                  = var.jira_cloud_id
  example_jira_issue_id          = var.example_jira_issue_id
  bulk_sanitized_expiration_days = var.bulk_sanitized_expiration_days
  bulk_input_expiration_days     = var.bulk_input_expiration_days
  # Uncomment the following line if you want to apply KMS encryption on your SSM parameters
  #  aws_ssm_key_id                 = aws_kms_key.key.key_id
}

# rename done in v0.4.15
moved {
  from = module.psoxy-aws-google-workspace
  to   = module.psoxy
}


## TODO: requires targeted apply to create key first, bc value of key_id determines map content
## in example
#resource "aws_kms_key" "key" {
#  description             = "KMS key for Psoxy"
#  enable_key_rotation     = true
#  is_enabled              = true
#}

# if you generated these, you may want them to import back into your data warehouse
output "lookup_tables" {
  value = module.psoxy.lookup_tables
}

output "todos_1" {
  description = "List of todo steps to complete 1st, in markdown format."
  value       = join("\n", module.psoxy.todos_1)
}

output "todos_2" {
  description = "List of todo steps to complete 2nd, in markdown format."
  value       = join("\n", module.psoxy.todos_2)
}

output "todos_3" {
  description = "List of todo steps to complete 3rd, in markdown format."
  value       = join("\n", module.psoxy.todos_3)
}

/** Vault dev config for local testing **/

# used for peering to Vault Cloud HVN
resource "aws_vpc" "psoxy_vpc" {
  cidr_block = var.vpc_ip_block
}

provider "vault" {
  address = var.vault_addr

  # NOTE: set a token in your env vars, eg
  # export VAULT_TOKEN=...
}

#module "aws_vault_auth" {
#  source = "../../modules/aws-vault-auth"
#
#  aws_vault_role_arn = var.aws_vault_role_arn
#}
#
#module "vault_psoxy" {
#  for_each = module.psoxy-aws-google-workspace.instances
#
#  source = "../../modules/vault-psoxy"
#
#  instance_id           = each.key
#}
#
## BEGIN AWS iam-based Auth
#module "aws_vault_connection_gcal" {
#  for_each = module.psoxy-aws-google-workspace.instances
#
#  source = "../../modules/aws-vault-access"
#
#  aws_auth_backend_name = module.aws_vault_auth.vault_aws_auth_backend_path
#  instance_id           = each.key
#  role_arn              = each.value.instance_role_arn
#  vault_policy_name     = module.vault_psoxy[each.key].vault_policy_name
#}
## END AWS iam-based Auth

#
## BEGIN periodic token-based vault auth
#module "aws_vault_token" {
#  for_each = module.psoxy-aws-google-workspace.instances
#
#  source = "../../modules/vault-access-token"
#
#  instance_id           = each.key
#
#  vault_policy_name     = module.vault_psoxy[each.key].vault_policy_name
#}
#
#locals {
#  tokens_to_set = [ for k, v in module.aws_vault_token : " - on `${k}` set `VAULT_TOKEN` = `${v.vault_token}`"]
#}
#
## unfortunately, no way to backfill these on per-lambda basis without deeper refactor
#resource "local_file" "fill_token_vars" {
#  filename = "TODO 0 - fill VAULT_TOKEN env vars.md"
#  content = <<EOT
## TODO - fill token env vars on lambdas
#
#Via AWS console --> Lambdas:
#${join("\n", local.tokens_to_set)}
#
#EOT
#}
#
## END periodic token-based vault auth