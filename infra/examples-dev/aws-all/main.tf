terraform {
  required_providers {
    # for the infra that will host Psoxy instances
    aws = {
      source  = "hashicorp/aws"
      version = "~> 4.12"
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


## Data Sources configuration
## (eg, sources you want to connect to Worklytics)

# general cases
module "worklytics_connectors" {
  source = "../../modules/worklytics-connectors"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-connectors?ref=v0.4.26"

  enabled_connectors    = var.enabled_connectors
  example_jira_issue_id = var.example_jira_issue_id
  jira_cloud_id         = var.jira_cloud_id
  jira_server_url       = var.jira_server_url
  salesforce_domain     = var.salesforce_domain
}

# sources which require additional dependencies are split into distinct Terraform files, following
# the naming convention of `{source-identifier}.tf`, eg `msft-365.tf`
# lines below merge results of those files back into single maps of sources
locals {
  api_connectors = merge(
    module.worklytics_connectors.enabled_api_connectors,
    module.worklytics_connectors_google_workspace.enabled_api_connectors,
    local.msft_api_connectors_with_auth,
    {}
  )

  source_authorization_todos = concat(
    module.worklytics_connectors.todos,
    module.worklytics_connectors_google_workspace.todos,
    module.worklytics_connectors_msft_365.todos,
    []
  )

  max_auth_todo_step = max(
    module.worklytics_connectors.next_todo_step,
    module.worklytics_connectors_google_workspace.next_todo_step,
    module.worklytics_connectors_msft_365.next_todo_step,
    0
  )
}


locals {
  bulk_connectors = merge(
    module.worklytics_connectors.enabled_bulk_connectors,
    var.custom_bulk_connectors,
  )
}


## Host platform (AWS) configuration

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

module "psoxy" {
  source = "../../modules/aws-host"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-host?ref=v0.4.26"

  environment_name               = var.environment_name
  aws_account_id                 = var.aws_account_id
  aws_ssm_param_root_path        = var.aws_ssm_param_root_path
  psoxy_base_dir                 = var.psoxy_base_dir
  deployment_bundle              = var.deployment_bundle
  install_test_tool              = var.install_test_tool
  provision_testing_infra        = var.provision_testing_infra
  force_bundle                   = var.force_bundle
  caller_gcp_service_account_ids = var.caller_gcp_service_account_ids
  caller_aws_arns                = var.caller_aws_arns
  non_production_connectors      = var.non_production_connectors
  custom_api_connector_rules     = var.custom_api_connector_rules
  lookup_table_builders          = var.lookup_table_builders
  general_environment_variables  = var.general_environment_variables
  function_env_kms_key_arn       = var.project_aws_kms_key_arn
  aws_ssm_key_id                 = var.project_aws_kms_key_arn
  bulk_sanitized_expiration_days = var.bulk_sanitized_expiration_days
  bulk_input_expiration_days     = var.bulk_input_expiration_days
  api_connectors                 = local.api_connectors
  bulk_connectors                = local.bulk_connectors
  custom_bulk_connector_rules    = var.custom_bulk_connector_rules
  todo_step                      = local.max_auth_todo_step
}

## Worklytics connection configuration
#  as of June 2023, this just outputs TODO files, but would provision connections via future
#  Worklytics API / Terraform provider

locals {
  all_connectors = merge(local.api_connectors, local.bulk_connectors)
  all_instances  = merge(module.psoxy.bulk_connector_instances, module.psoxy.api_connector_instances)
}

module "connection_in_worklytics" {
  for_each = local.all_instances

  source = "../../modules/worklytics-psoxy-connection-generic"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection-generic?ref=v0.4.26"

  psoxy_host_platform_id = local.host_platform_id
  psoxy_instance_id      = each.key
  worklytics_host        = var.worklytics_host
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
  value       = var.todos_as_outputs ? join("\n", local.source_authorization_todos) : null
}

output "todos_2" {
  description = "List of todo steps to complete 2nd, in markdown format."
  value       = var.todos_as_outputs ? join("\n", module.psoxy.todos) : null
}

output "todos_3" {
  description = "List of todo steps to complete 3rd, in markdown format."
  value       = var.todos_as_outputs ? join("\n", values(module.connection_in_worklytics)[*].todo) : null
}
