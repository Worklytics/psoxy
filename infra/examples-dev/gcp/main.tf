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

module "worklytics_connectors_msft_365" {
  source = "../../modules/worklytics-connectors-msft-365"

  enabled_connectors     = var.enabled_connectors
  msft_tenant_id         = var.msft_tenant_id
  msft_owners_email      = var.msft_owners_email
  example_msft_user_guid = var.example_msft_user_guid
}


locals {
  rest_connectors = merge(
    module.worklytics_connectors.enabled_rest_connectors,
    module.worklytics_connectors_google_workspace.enabled_rest_connectors,
    module.worklytics_connectors_msft_365.enabled_rest_connectors
  )

  bulk_connectors = merge(
    module.worklytics_connectors.enabled_bulk_connectors,
    var.custom_bulk_connectors
  )
}

module "psoxy" {
  source = "../../modules/gcp-host"
  # source = "git::https://github.com/worklytics/psoxy//infra/modular-examples/gcp?ref=v0.4.25"

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

module "rest_to_worklytics" {
  for_each = module.psoxy.rest_connector_instances

  source = "../../modules/worklytics-psoxy-connection"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection?ref=v0.4.25"

  psoxy_host_platform_id = local.host_platform_id
  psoxy_instance_id      = each.key
  connector_id           = try(local.rest_connectors[each.key].worklytics_connector_id, "")
  psoxy_endpoint_url     = each.value.endpoint_url
  display_name           = "${title(each.key)} via Psoxy"
  todo_step              = module.psoxy.next_todo_step
}

module "bulk_to_worklytics" {
  for_each = module.psoxy.bulk_connector_instances

  source = "../../modules/worklytics-psoxy-connection-generic"

  psoxy_host_platform_id = local.host_platform_id
  psoxy_instance_id      = each.key
  connector_id           = try(local.bulk_connectors[each.key].worklytics_connector_id, "")
  display_name           = try(local.bulk_connectors[each.key].worklytics_connector_name, "${each.value.display_name} via Psoxy")
  todo_step              = module.psoxy.next_todo_step

  settings_to_provide = merge({
    "Bucket Name" = each.value.sanitized_bucket
  }, try(each.value.settings_to_provide, {}))
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
      module.worklytics_connectors_google_workspace.todos,
      module.worklytics_connectors_msft_365.todos
  )) : null
}

output "todos_2" {
  description = "List of todo steps to complete 2nd, in markdown format."
  value       = var.todos_as_outputs ? join("\n", module.psoxy.todos) : null
}

output "todos_3" {
  description = "List of todo steps to complete 3rd, in markdown format."
  value = var.todos_as_outputs ? join("\n", concat(
    module.bulk_to_worklytics.todos,
    module.rest_to_worklytics.todos
  )) : null
}
