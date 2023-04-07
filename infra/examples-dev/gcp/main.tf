terraform {
  required_providers {
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

  # example remove backend (this GCS bucket must already be provisioned, and GCP user executing
  # terraform must be able to read/write to it)
  #  backend "gcs" {
  #    bucket  = "tf-state-prod"
  #    prefix  = "terraform/state"
  #  }
}

provider "azuread" {
  tenant_id = var.msft_tenant_id
}

module "psoxy" {
  source = "../../modular-examples/gcp"
  # source = "git::https://github.com/worklytics/psoxy//infra/modular-examples/gcp?ref=v0.4.18"

  gcp_project_id                 = var.gcp_project_id
  environment_name               = var.environment_name
  worklytics_sa_emails           = var.worklytics_sa_emails
  connector_display_name_suffix  = var.connector_display_name_suffix
  psoxy_base_dir                 = var.psoxy_base_dir
  force_bundle                   = var.force_bundle
  gcp_region                     = var.gcp_region
  replica_regions                = var.replica_regions
  enabled_connectors             = var.enabled_connectors
  non_production_connectors      = var.non_production_connectors
  custom_bulk_connectors         = var.custom_bulk_connectors
  google_workspace_example_user  = var.google_workspace_example_user
  google_workspace_example_admin = var.google_workspace_example_admin
  general_environment_variables  = var.general_environment_variables
  pseudonymize_app_ids           = var.pseudonymize_app_ids
  salesforce_domain              = var.salesforce_domain
  bulk_input_expiration_days     = var.bulk_input_expiration_days
  bulk_sanitized_expiration_days = var.bulk_sanitized_expiration_days
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
