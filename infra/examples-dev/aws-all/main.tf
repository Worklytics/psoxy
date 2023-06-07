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



locals {
  host_platform_id = "AWS"
}

module "worklytics_connectors" {
  source = "../../modules/worklytics-connectors"

  enabled_connectors    = var.enabled_connectors
  example_jira_issue_id = var.example_jira_issue_id
  jira_cloud_id         = var.jira_cloud_id
  jira_server_url       = var.jira_server_url
  salesforce_domain     = var.salesforce_domain
}

module "worklytics_connectors_google_workspace" {
  source = "../../modules/worklytics-connectors-google-workspace"

  enabled_connectors             = var.enabled_connectors
  gcp_project_id                 = var.gcp_project_id
  google_workspace_example_user  = var.google_workspace_example_user
  google_workspace_example_admin = var.google_workspace_example_admin
}

# BEGIN MSFT

module "worklytics_connectors_msft_365" {
  source = "../../modules/worklytics-connectors-msft-365"

  enabled_connectors     = var.enabled_connectors
  environment_id         = var.environment_name
  msft_tenant_id         = var.msft_tenant_id
  example_msft_user_guid = var.example_msft_user_guid
  msft_owners_email      = var.msft_owners_email
  todo_step              = 1
}

provider "azuread" {
  tenant_id = var.msft_tenant_id
}

locals {
  source_authorization_todos = concat(
    module.worklytics_connectors.todos,
    module.worklytics_connectors_google_workspace.todos,
    module.worklytics_connectors_msft_365.todos
  )


  msft_365_enabled = length(module.worklytics_connectors_msft_365.enabled_api_connectors) > 0
  developer_provider_name = "azure-access"
}

# BEGIN MSFT AUTH
# q: better to extract this into module?
#   - as this is a 'root' Terraform configuration, it will be 1 rather than 3 clones of git repos,
#     and 1 rather than 3 places to change version numbers
#   - raises level of abstraction, but not very "flat" Terraform style
#   - but given that may be swapped out for certificate-based auth, raising level of abstraction
#  seems like a good idea; this module shouldn't know *details* of aws-msft-auth-identity-federation
#  vs aws-msft-auth-certificate right?
#  --> although there is a difference that one fills ENV vars, and other secrets

data "aws_region" "current" {

}

module "cognito_identity_pool" {
  count = local.msft_365_enabled ? 1 : 0 # only provision identity pool if MSFT-365 connectors are enabled

  source = "../../modules/aws-cognito-pool"

  developer_provider_name = local.developer_provider_name
  name                    = "azure-ad-federation"
}

module "cognito_identity" {
  count = local.msft_365_enabled ? 1 : 0 # only provision identity pool if MSFT-365 connectors are enabled

  source = "../../modules/aws-cognito-identity-cli"

  aws_region       = data.aws_region.current.id
  aws_role         = var.aws_assume_role_arn
  identity_pool_id = module.cognito_identity_pool[0].pool_id
  login_ids        = {
    for k, v in module.worklytics_connectors_msft_365.enabled_api_connectors :
      k => "${local.developer_provider_name}=${v.connector.application_id}"
  }
}

module "msft_connection_auth_federation" {
  for_each = module.worklytics_connectors_msft_365.enabled_api_connectors

  source = "../../modules/azuread-federated-credentials"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-federated-credentials?ref=v0.4.25"

  application_object_id = each.value.connector.id
  display_name          = "AccessFromAWS"
  description           = "AWS federation to be used for psoxy Connector - ${each.value.display_name}${var.connector_display_name_suffix}"
  issuer                = "https://cognito-identity.amazonaws.com"
  audience              = module.cognito_identity_pool[0].pool_id
  subject               = module.cognito_identity[0].identity_id[each.key]
}

locals {
  msft_api_connectors_with_auth = { for k, msft_connector in module.worklytics_connectors_msft_365.enabled_api_connectors :
    k => merge(msft_connector, {
      environment_variables = merge(try(msft_connector.environment_variables, {}),
        {
          IDENTITY_POOL_ID     = module.cognito_identity_pool[0].pool_id,
          IDENTITY_ID          = module.cognito_identity[0].identity_id[k],
          DEVELOPER_NAME_ID    = local.developer_provider_name
        }
      )
    })
  }

  # END MSFT AUTH

  api_connectors = merge(
    module.worklytics_connectors.enabled_api_connectors,
    module.worklytics_connectors_google_workspace.enabled_api_connectors,
    local.msft_api_connectors_with_auth
  )

  bulk_connectors = merge(
    module.worklytics_connectors.enabled_bulk_connectors,
    var.custom_bulk_connectors,
  )
}

module "psoxy" {
  source = "../../modules/aws-host"

  aws_account_id                 = var.aws_account_id
  aws_assume_role_arn            = var.aws_assume_role_arn # role that can test the instances (lambdas)
  aws_region                     = var.aws_region
  aws_ssm_param_root_path        = var.aws_ssm_param_root_path
  psoxy_base_dir                 = var.psoxy_base_dir
  install_test_tool              = var.install_test_tool
  provision_testing_infra        = var.provision_testing_infra
  force_bundle                   = var.force_bundle
  caller_gcp_service_account_ids = var.caller_gcp_service_account_ids
  caller_aws_arns                = var.caller_aws_arns
  non_production_connectors      = var.non_production_connectors
  custom_api_connector_rules     = var.custom_api_connector_rules
  lookup_table_builders          = var.lookup_table_builders
  general_environment_variables  = var.general_environment_variables
  environment_name               = var.environment_name
  bulk_sanitized_expiration_days = var.bulk_sanitized_expiration_days
  bulk_input_expiration_days     = var.bulk_input_expiration_days
  api_connectors                 = local.api_connectors
  bulk_connectors                = local.bulk_connectors
  todo_step                      = max(module.worklytics_connectors.next_todo_step, module.worklytics_connectors_google_workspace.next_todo_step, module.worklytics_connectors_msft_365.next_todo_step)
}

locals {
  all_connectors = merge(local.api_connectors, local.bulk_connectors)
  all_instances  = merge(module.psoxy.bulk_connector_instances, module.psoxy.api_connector_instances)
}


module "connection_in_worklytics" {
  for_each = local.all_instances

  source = "../../modules/worklytics-psoxy-connection-generic"

  psoxy_host_platform_id = local.host_platform_id
  psoxy_instance_id      = each.key
  connector_id           = try(local.all_connectors[each.key].worklytics_connector_id, "")
  display_name           = try(local.all_connectors[each.key].worklytics_connector_name, "${local.all_connectors[each.key].display_name} via Psoxy")
  todo_step              = module.psoxy.next_todo_step

  settings_to_provide = merge(
    # Source API case
    try({
      "Psoxy Base URL" = each.value.endpoint_url
    }, {}),
    # Source Bucket (file) case
    try({
      "Bucket Name" = each.value.sanitized_bucket_name
    }, {}),
    try(each.value.settings_to_provide, {}))
}

output "path_to_deployment_jar" {
  description = "Path to the package to deploy (JAR)."
  value       = module.psoxy.path_to_deployment_jar
}

output "todos_1" {
  description = "List of todo steps to complete 1st, in markdown format."
  value = var.todos_as_outputs ? join("\n", local.source_authorization_todos) : null
}

output "todos_2" {
  description = "List of todo steps to complete 2nd, in markdown format."
  value       = var.todos_as_outputs ? join("\n", module.psoxy.todos) : null
}

output "todos_3" {
  description = "List of todo steps to complete 3rd, in markdown format."
  value       = var.todos_as_outputs ? join("\n", values(module.connection_in_worklytics)[*].todo) : null
}
