terraform {
  required_version = ">= 1.3, < 1.10"

  required_providers {
    # for the infra that will host Psoxy instances
    aws = {
      source  = "hashicorp/aws"
      version = "~> 4.22"
    }
  }

  # NOTE: Terraform backend block is configured in a separate 'backend.tf' file, as expect everyone
  # to customize it for their own use; whereas `main.tf` should be largely consistent across
  # deployments
}


## Data Sources configuration
## (eg, sources you want to connect to Worklytics)

# general cases
module "worklytics_connectors" {
  source = "../../modules/worklytics-connectors"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-connectors?ref=rc-v0.5.0"

  enabled_connectors               = var.enabled_connectors
  jira_cloud_id                    = var.jira_cloud_id
  jira_server_url                  = var.jira_server_url
  jira_example_issue_id            = var.jira_example_issue_id
  salesforce_domain                = var.salesforce_domain
  github_api_host                  = var.github_api_host
  github_enterprise_server_host    = var.github_enterprise_server_host
  github_enterprise_server_version = var.github_enterprise_server_version
  github_installation_id           = var.github_installation_id
  github_organization              = var.github_organization
  github_example_repository        = var.github_example_repository
  salesforce_example_account_id    = var.salesforce_example_account_id
  todos_as_local_files             = var.todos_as_local_files
  todo_step                        = 1
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

  default_tags {
    tags = var.default_tags
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
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-host?ref=rc-v0.5.0"

  environment_name                     = var.environment_name
  aws_account_id                       = var.aws_account_id
  aws_ssm_param_root_path              = var.aws_ssm_param_root_path
  aws_secrets_manager_path             = coalesce(var.aws_secrets_manager_path, "${var.environment_name}_")
  psoxy_base_dir                       = var.psoxy_base_dir
  deployment_bundle                    = var.deployment_bundle
  install_test_tool                    = var.install_test_tool
  provision_testing_infra              = var.provision_testing_infra
  force_bundle                         = var.force_bundle
  caller_gcp_service_account_ids       = var.caller_gcp_service_account_ids
  caller_aws_arns                      = var.caller_aws_arns
  non_production_connectors            = var.non_production_connectors
  custom_api_connector_rules           = var.custom_api_connector_rules
  lookup_table_builders                = var.lookup_table_builders
  pseudonymize_app_ids                 = var.pseudonymize_app_ids
  email_canonicalization               = var.email_canonicalization
  general_environment_variables        = var.general_environment_variables
  function_env_kms_key_arn             = var.project_aws_kms_key_arn
  logs_kms_key_arn                     = var.project_aws_kms_key_arn
  log_retention_days                   = var.log_retention_days
  aws_ssm_key_id                       = var.project_aws_kms_key_arn
  use_api_gateway_v2                   = var.use_api_gateway_v2
  aws_lambda_execution_role_policy_arn = var.aws_lambda_execution_role_policy_arn
  iam_roles_permissions_boundary       = var.iam_roles_permissions_boundary
  secrets_store_implementation         = var.secrets_store_implementation
  bulk_sanitized_expiration_days       = var.bulk_sanitized_expiration_days
  bulk_input_expiration_days           = var.bulk_input_expiration_days
  api_connectors                       = local.api_connectors
  bulk_connectors                      = local.bulk_connectors
  provision_bucket_public_access_block = var.provision_bucket_public_access_block
  custom_bulk_connector_rules          = var.custom_bulk_connector_rules
  custom_bulk_connector_arguments      = var.custom_bulk_connector_arguments
  todo_step                            = local.max_auth_todo_step
  todos_as_local_files                 = var.todos_as_local_files


  #  vpc_config = {
  #    vpc_id             = aws_default_vpc.default.id
  #    security_group_ids = [aws_security_group.default.id]
  #    subnet_ids         = [aws_default_subnet.default.id]
  #  }
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

  source = "../../modules/worklytics-psoxy-connection-aws"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection-aws?ref=rc-v0.5.0"

  psoxy_instance_id    = each.key
  worklytics_host      = var.worklytics_host
  aws_region           = var.aws_region
  aws_role_arn         = module.psoxy.caller_role_arn
  psoxy_endpoint_url   = try(each.value.endpoint_url, null)
  bucket_name          = try(each.value.sanitized_bucket, null)
  connector_id         = try(local.all_connectors[each.key].worklytics_connector_id, "")
  display_name         = try(local.all_connectors[each.key].worklytics_connector_name, "${local.all_connectors[each.key].display_name} via Psoxy")
  todo_step            = module.psoxy.next_todo_step
  todos_as_local_files = var.todos_as_local_files

  connector_settings_to_provide = try(each.value.settings_to_provide, {})

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

# although should be sensitive such that Terraform won't echo it to command line or expose it, leave
# commented out in example until needed
# if you uncomment it, you will then be able to obtain the value through `terraform output --raw pseudonym_salt`
#output "pseudonym_salt" {
#  description = "Value used to salt pseudonyms (SHA-256) hashes. If migrate to new deployment, you should copy this value."
#  value       = module.psoxy.pseudonym_salt
#  sensitive   = true
#}
