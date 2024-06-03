terraform {
  required_providers {
    # for the infra that will host Psoxy instances
    aws = {
      source  = "hashicorp/aws"
      version = "~> 4.12"
    }

    # for API connections to Microsoft 365 (comment this out if unused)
    azuread = {
      version = "~> 2.3"
    }

    # for the API connections to Google Workspace
    google = {
      version = ">= 3.74, <= 5.0"
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
  #    bucket = "terraform_state_bucket" # fill with S3 bucket where you want the statefile to be
  #    key    = "prod_state" # fill with path where you want state file to be stored
  #    region = "us-east-1" # cannot be a variable
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

module "psoxy" {
  source = "../../modular-examples/aws"
  # source = "git::https://github.com/worklytics/psoxy//infra/modular-examples/aws?ref=rc-v0.4.55"

  aws_account_id                 = var.aws_account_id
  aws_assume_role_arn            = var.aws_assume_role_arn # role that can test the instances (lambdas)
  aws_region                     = var.aws_region
  aws_ssm_param_root_path        = var.aws_ssm_param_root_path
  psoxy_base_dir                 = var.psoxy_base_dir
  install_test_tool              = var.install_test_tool
  provision_testing_infra        = var.provision_testing_infra
  force_bundle                   = var.force_bundle
  worklytics_host                = var.worklytics_host
  caller_gcp_service_account_ids = var.caller_gcp_service_account_ids
  caller_aws_arns                = var.caller_aws_arns
  enabled_connectors             = var.enabled_connectors
  non_production_connectors      = var.non_production_connectors
  connector_display_name_suffix  = var.connector_display_name_suffix
  custom_bulk_connectors         = var.custom_bulk_connectors
  custom_rest_rules              = var.custom_rest_rules
  lookup_table_builders          = var.lookup_table_builders
  msft_tenant_id                 = var.msft_tenant_id
  msft_owners_email              = var.msft_owners_email
  general_environment_variables  = var.general_environment_variables
  salesforce_domain              = var.salesforce_domain
  jira_server_url                = var.jira_server_url
  jira_cloud_id                  = var.jira_cloud_id
  example_jira_issue_id          = var.example_jira_issue_id
  github_installation_id         = var.github_installation_id
  github_organization            = var.github_organization
  github_example_repository      = var.github_example_repository
  gcp_project_id                 = var.gcp_project_id
  google_workspace_example_admin = var.google_workspace_example_admin
  google_workspace_example_user  = var.google_workspace_example_user
  environment_name               = var.environment_name
  bulk_sanitized_expiration_days = var.bulk_sanitized_expiration_days
  bulk_input_expiration_days     = var.bulk_input_expiration_days
}

# if you generated these, you may want them to import back into your data warehouse
output "lookup_tables" {
  value = module.psoxy.lookup_tables
}

output "path_to_deployment_jar" {
  description = "Path to the package to deploy (JAR)."
  value       = module.psoxy.path_to_deployment_jar
}

output "todos_1" {
  description = "List of todo steps to complete 1st, in markdown format."
  value       = var.todos_as_outputs ? join("\n", module.psoxy.todos_1) : null
}

output "todos_2" {
  description = "List of todo steps to complete 2nd, in markdown format."
  value       = var.todos_as_outputs ? join("\n", module.psoxy.todos_2) : null
}

output "todos_3" {
  description = "List of todo steps to complete 3rd, in markdown format."
  value       = var.todos_as_outputs ? join("\n", module.psoxy.todos_3) : null
}
