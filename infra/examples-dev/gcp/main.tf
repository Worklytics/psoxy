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

  # example remote backend (this GCS bucket must already be provisioned, and GCP user executing
  # terraform must be able to read/write to it)
  #  backend "gcs" {
  #    bucket  = "tf-state-prod"
  #    prefix  = "proxy/terraform-state"
  #  }
}

provider "azuread" {
  tenant_id = var.msft_tenant_id
}

provider "google" {
  impersonate_service_account = var.gcp_terraform_sa_account_email
}

module "psoxy" {
  source = "../../modular-examples/gcp"
  # source = "git::https://github.com/worklytics/psoxy//infra/modular-examples/gcp?ref=v0.4.20"

  gcp_project_id                 = var.gcp_project_id
  environment_id                 = var.environment_id
  config_parameter_prefix        = var.config_parameter_prefix
  worklytics_sa_emails           = var.worklytics_sa_emails
  psoxy_base_dir                 = var.psoxy_base_dir
  force_bundle                   = var.force_bundle
  install_test_tool              = var.install_test_tool
  gcp_region                     = var.gcp_region
  replica_regions                = var.replica_regions
  enabled_connectors             = var.enabled_connectors
  non_production_connectors      = var.non_production_connectors
  custom_rest_rules              = var.custom_rest_rules
  custom_bulk_connectors         = var.custom_bulk_connectors
  google_workspace_example_user  = var.google_workspace_example_user
  google_workspace_example_admin = var.google_workspace_example_admin
  general_environment_variables  = var.general_environment_variables
  pseudonymize_app_ids           = var.pseudonymize_app_ids
  salesforce_domain              = var.salesforce_domain
  bulk_input_expiration_days     = var.bulk_input_expiration_days
  bulk_sanitized_expiration_days = var.bulk_sanitized_expiration_days
  lookup_tables                  = var.lookup_tables
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
