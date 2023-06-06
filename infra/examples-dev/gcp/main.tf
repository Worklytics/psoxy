terraform {
  required_providers {
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

  # example remote backend (this GCS bucket must already be provisioned, and GCP user executing
  # terraform must be able to read/write to it)
  #  backend "gcs" {
  #    bucket  = "tf-state-prod"
  #    prefix  = "proxy/terraform-state"
  #  }
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

locals {
  rest_connectors = merge(
    module.worklytics_connectors.enabled_rest_connectors,
    module.worklytics_connectors_google_workspace.enabled_rest_connectors,
  )

  bulk_connectors = merge(
    module.worklytics_connectors.enabled_bulk_connectors,
    var.custom_bulk_connectors,
  )
}

module "psoxy" {
  source = "../../modules/gcp-host"

  gcp_project_id                 = var.gcp_project_id
  environment_id                 = var.environment_id
  config_parameter_prefix        = var.config_parameter_prefix
  worklytics_sa_emails           = var.worklytics_sa_emails
  psoxy_base_dir                 = var.psoxy_base_dir
  force_bundle                   = var.force_bundle
  install_test_tool              = var.install_test_tool
  gcp_region                     = var.gcp_region
  replica_regions                = var.replica_regions
  rest_connectors                = local.rest_connectors
  bulk_connectors                = local.bulk_connectors
  non_production_connectors      = var.non_production_connectors
  custom_rest_rules              = var.custom_rest_rules
  general_environment_variables  = var.general_environment_variables
  pseudonymize_app_ids           = var.pseudonymize_app_ids
  bulk_input_expiration_days     = var.bulk_input_expiration_days
  bulk_sanitized_expiration_days = var.bulk_sanitized_expiration_days
  lookup_tables                  = var.lookup_tables
}

locals {
  all_connectors = merge(local.rest_connectors, local.bulk_connectors)
  all_instances  = merge(module.psoxy.bulk_connector_instances, module.psoxy.rest_connector_instances)
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
      "Bucket Name" = each.value.sanitized_bucket
    }, {}),
  try(each.value.settings_to_provide, {}))
}

output "path_to_deployment_jar" {
  description = "Path to the package to deploy (JAR)."
  value       = module.psoxy.path_to_deployment_jar
}

output "todos_1" {
  description = "List of todo steps to complete 1st, in markdown format."
  value = var.todos_as_outputs ? join("\n",
    concat(
      module.worklytics_connectors.todos,
      module.worklytics_connectors_google_workspace.todos
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
