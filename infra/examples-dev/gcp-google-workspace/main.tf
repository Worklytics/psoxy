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

# NOTE: if you don't have perms to provision a GCP project in your billing account, you can have
# someone else create one and than import it:
#  `terraform import google_project.psoxy-project your-psoxy-project-id`
# either way, we recommend the project be used exclusively to host psoxy instances corresponding to
# a single worklytics account
resource "google_project" "psoxy-project" {
  name            = "Psoxy%{if var.environment_name != ""} - ${var.environment_name}%{endif}"
  project_id      = var.gcp_project_id
  billing_account = var.gcp_billing_account_id
  folder_id       = var.gcp_folder_id # if project is at top-level of your GCP organization, rather than in a folder, comment this line out
  # org_id          = var.gcp_org_id # if project is in a GCP folder, this value is implicit and this line should be commented out
}

module "psoxy-gcp-google-workspace" {
  source = "../../modular-examples/gcp-google-workspace"
  # source = "git::https://github.com/worklytics/psoxy//infra/modular-examples/aws-google-workspace?ref=v0.4.9"

  gcp_project_id                = google_project.psoxy-project.project_id
  environment_name              = var.environment_name
  worklytics_sa_emails          = var.worklytics_sa_emails
  connector_display_name_suffix = var.connector_display_name_suffix
  psoxy_base_dir                = var.psoxy_base_dir
  force_bundle                  = var.force_bundle
  gcp_region                    = var.gcp_region
  replica_regions               = var.replica_regions
  enabled_connectors            = var.enabled_connectors
  non_production_connectors     = var.non_production_connectors
  custom_bulk_connectors        = var.custom_bulk_connectors
  google_workspace_example_user = var.google_workspace_example_user
  general_environment_variables = var.general_environment_variables

  depends_on = [
    google_project.psoxy-project
  ]
}

moved {
  from = module.worklytics_connector_specs
  to   = module.psoxy-gcp-google-workspace.module.worklytics_connector_specs
}

moved {
  from = module.psoxy-gcp
  to   = module.psoxy-gcp-google-workspace.module.psoxy-gcp
}

moved {
  from = module.global_secrets
  to   = module.psoxy-gcp-google-workspace.module.global_secrets
}

moved {
  from = module.google-workspace-connection
  to   = module.psoxy-gcp-google-workspace.module.google-workspace-connection
}

moved {
  from = module.google-workspace-connection-auth
  to   = module.psoxy-gcp-google-workspace.module.google-workspace-connection-auth
}

moved {
  from = module.google-workspace-key-secrets
  to   = module.psoxy-gcp-google-workspace.module.google-workspace-key-secrets
}

moved {
  from = module.psoxy-google-workspace-connector
  to   = module.psoxy-gcp-google-workspace.module.psoxy-google-workspace-connector
}

moved {
  from = module.worklytics-psoxy-connection-google-workspace
  to   = module.psoxy-gcp-google-workspace.module.worklytics-psoxy-connection-google-workspace
}

moved {
  from = google_service_account.long_auth_connector_sa
  to   = module.psoxy-gcp-google-workspace.google_service_account.long_auth_connector_sa
}

moved {
  from = module.connector-long-auth-block
  to   = module.psoxy-gcp-google-workspace.module.connector-long-auth-block
}

moved {
  from = module.long-auth-token-secret-fill-instructions
  to   = module.psoxy-gcp-google-workspace.module.long-auth-token-secret-fill-instructions
}

moved {
  from = module.connector-long-auth-create-function
  to   = module.psoxy-gcp-google-workspace.module.connector-long-auth-function
}

moved {
  from = module.worklytics-psoxy-connection-long-auth
  to   = module.psoxy-gcp-google-workspace.module.worklytics-psoxy-connection-long-auth
}

moved {
  from = module.psoxy-gcp-bulk
  to   = module.psoxy-gcp-google-workspace.module.psoxy-gcp-bulk
}

