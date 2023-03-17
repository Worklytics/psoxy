terraform {
  required_providers {
    google = {
      version = "~> 4.12"
    }
  }

  # we recommend you use a secure location for your Terraform state (such as GCS bucket), as it
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

# Google user or service account which Terraform is authenticated as must be authorized to
# provision resources in this project
data "google_project" "psoxy-project" {
  project_id = var.gcp_project_id
}

module "psoxy" {
  # source = "../../modular-examples/gcp-google-workspace"
  source = "git::https://github.com/worklytics/psoxy//infra/modular-examples/gcp-google-workspace?ref=rc-v0.4.15"

  gcp_project_id                 = data.google_project.psoxy-project.project_id
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
  salesforce_domain              = var.salesforce_domain
}

moved {
  from = module.psoxy-gcp-google-workspace
  to   = module.psoxy
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
