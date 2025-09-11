terraform {
  required_version = ">= 1.3, < 2.0"

  required_providers {
    google = {
      version = "~> 5.0" # TODO: actually go to 6.0 for proxy v0.5
    }
  }

  # NOTE: Terraform backend block is configured in a separate 'backend.tf' file, as expect everyone
  # to customize it for their own use; whereas `main.tf` should be largely consistent across
  # deployments
}

provider "google" {
  impersonate_service_account = var.gcp_terraform_sa_account_email
}

locals {
  host_platform_id = "GCP"
}

# TODO: this has 5 remote modules; combine some?
#  eg, worklytics-connectors + gcp-host + worklytics-psoxy-connection-generic into a single
#     gcp-host-for-worklytics? poor TF style, but simplifies root module?

# in effect, these are for sources for which authentication/authorization cannot (or need not)
# be provisioned via Terraform, so doesn't add any dependencies
# call this 'generic_source_connectors'?
module "worklytics_connectors" {
  source = "../../modules/worklytics-connectors"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-connectors?ref=rc-v0.5.9"

  enabled_connectors                       = var.enabled_connectors
  chat_gpt_enterprise_example_workspace_id = var.chat_gpt_enterprise_example_workspace_id
  confluence_example_cloud_id              = var.confluence_example_cloud_id
  confluence_example_group_id              = var.confluence_example_group_id
  jira_cloud_id                            = var.jira_cloud_id
  jira_server_url                          = var.jira_server_url
  jira_example_issue_id                    = var.jira_example_issue_id
  salesforce_domain                        = var.salesforce_domain
  github_api_host                          = var.github_api_host
  github_enterprise_server_host            = var.github_enterprise_server_host
  github_enterprise_server_version         = var.github_enterprise_server_version
  github_installation_id                   = var.github_installation_id
  github_copilot_installation_id           = var.github_copilot_installation_id
  github_organization                      = var.github_organization
  github_example_repository                = var.github_example_repository
  salesforce_example_account_id            = var.salesforce_example_account_id
}

# sources which require additional dependencies are split into distinct Terraform files, following
# the naming convention of `{source-identifier}.tf`, eg `msft-365.tf`
# lines below merge results of those files back into single maps of sources
locals {
  api_connectors = merge(
    module.worklytics_connectors.enabled_api_connectors,
    module.worklytics_connectors_google_workspace.enabled_api_connectors,
    local.msft_api_connectors_with_auth,
    var.custom_api_connectors,
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

module "psoxy" {
  source = "../../modules/gcp-host"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-host?ref=rc-v0.5.9"

  gcp_project_id                    = var.gcp_project_id
  environment_name                  = var.environment_name
  config_parameter_prefix           = var.config_parameter_prefix
  default_labels                    = var.default_labels
  worklytics_sa_emails              = var.worklytics_sa_emails
  psoxy_base_dir                    = var.psoxy_base_dir
  deployment_bundle                 = var.deployment_bundle
  force_bundle                      = var.force_bundle
  install_test_tool                 = var.install_test_tool
  gcp_principals_authorized_to_test = var.gcp_principals_authorized_to_test
  gcp_region                        = var.gcp_region
  vpc_config                        = var.vpc_config
  secret_replica_locations          = var.secret_replica_locations
  api_connectors                    = local.api_connectors
  bulk_connectors                   = local.bulk_connectors
  webhook_collectors = { for k, v in var.webhook_collectors : k => merge(
    v,
    {
      example_payload = try(file(v.example_payload_file), null)
    }
  ) }
  non_production_connectors       = var.non_production_connectors
  custom_api_connector_rules      = var.custom_api_connector_rules
  general_environment_variables   = var.general_environment_variables
  pseudonymize_app_ids            = var.pseudonymize_app_ids
  email_canonicalization          = var.email_canonicalization
  bulk_input_expiration_days      = var.bulk_input_expiration_days
  bulk_sanitized_expiration_days  = var.bulk_sanitized_expiration_days
  custom_bulk_connector_rules     = var.custom_bulk_connector_rules
  custom_bulk_connector_arguments = var.custom_bulk_connector_arguments
  lookup_tables                   = var.lookup_tables
  custom_artifacts_bucket_name    = var.custom_artifacts_bucket_name
  custom_side_outputs             = var.custom_side_outputs
  todos_as_local_files            = var.todos_as_local_files
  todo_step                       = local.max_auth_todo_step
  bucket_force_destroy            = var.bucket_force_destroy
}

locals {
  all_connectors = merge(local.api_connectors, local.bulk_connectors)
  all_instances  = merge(module.psoxy.bulk_connector_instances, module.psoxy.api_connector_instances)
}

module "connection_in_worklytics" {
  for_each = local.all_instances

  source = "../../modules/worklytics-psoxy-connection-generic"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection-generic?ref=rc-v0.5.9"

  host_platform_id  = local.host_platform_id
  proxy_instance_id = each.key
  worklytics_host   = var.worklytics_host
  connector_id      = try(local.all_connectors[each.key].worklytics_connector_id, "")
  display_name      = try(local.all_connectors[each.key].worklytics_connector_name, "${local.all_connectors[each.key].display_name} via Psoxy")
  todo_step         = module.psoxy.next_todo_step

  settings_to_provide = merge(
    # Source API case
    try({
      "Psoxy Base URL" = each.value.endpoint_url
    }, {}),
    # Source Bucket (file) case
    try({
      "Bucket Name" = each.value.sanitized_bucket
    }, {}),
  try(each.value.settings_to_provide, {}))
}

output "path_to_deployment_jar" {
  description = "Path to the package to deploy (JAR)."
  value       = module.psoxy.path_to_deployment_jar
}

output "api_connector_instances" {
  value = { for k, v in module.psoxy.api_connector_instances : k => merge({
    endpoint_url = v.endpoint_url
    }, v.sanitized_bucket != null ? {
    sanitized_bucket = v.sanitized_bucket
    } : {})
  }
}

output "bulk_connector_instances" {
  value = { for k, v in module.psoxy.bulk_connector_instances : k => {
    sanitized_bucket = v.sanitized_bucket
  } }
}

output "webhook_collector_instances" {
  value = { for k, v in module.psoxy.webhook_collector_instances : k => {
    sanitized_bucket = v.output_sanitized_bucket_id
  } }
}

output "todos_1" {
  description = "List of todo steps to complete 1st, in markdown format."
  value = var.todos_as_outputs ? join("\n",
    concat(
      module.worklytics_connectors.todos,
      module.worklytics_connectors_google_workspace.todos,
      module.psoxy.setup_todos,
      []
  )) : null
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


