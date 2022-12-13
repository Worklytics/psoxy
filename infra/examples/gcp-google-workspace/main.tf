terraform {
  required_providers {
    google = {
      version = "~> 4.12"
    }
  }

  # if you leave this as local, you should backup/commit your TF state files
  backend "local" {
  }
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
  # source = "git::https://github.com/worklytics/psoxy//infra/modular-examples/gcp-google-workspace?ref=v0.4.8"

  gcp_project_id                 = google_project.psoxy-project.project_id
  environment_name               = var.environment_name
  worklytics_sa_emails           = var.worklytics_sa_emails
  connector_display_name_suffix  = var.connector_display_name_suffix
  psoxy_base_dir                 = var.psoxy_base_dir
  gcp_region                     = var.gcp_region
  replica_regions                = var.replica_regions
  enabled_connectors             = var.enabled_connectors
  non_production_connectors      = var.non_production_connectors
  custom_bulk_connectors         = var.custom_bulk_connectors
  google_workspace_example_user  = var.google_workspace_example_user
  general_environment_variables  = var.general_environment_variables

  depends_on = [
    google_project.psoxy-project
  ]
}
