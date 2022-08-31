terraform {
  required_providers {
    google = {
      version = ">= 4.13, <= 5.0"
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

# NOTE: if you don't have perms to provision a GCP project in your billing account, you can have
# someone else create one and than import it:
#  `terraform import google_project.psoxy-project your-psoxy-project-id`
# either way, we recommend the project be used exclusively to host psoxy instances corresponding to
# a single worklytics account
resource "google_project" "psoxy-project" {
  name            = "Worklytics Psoxy%{if var.environment_name != ""} - ${var.environment_name}%{endif}"
  project_id      = var.gcp_project_id
  billing_account = var.gcp_billing_account_id
  folder_id       = var.gcp_folder_id # if project is at top-level of your GCP organization, rather than in a folder, comment this line out
  # org_id          = var.org_id # if project is in a GCP folder, this value is implicit and this line should be commented out
}



module "psoxy-gcp" {
  # source = "../../modules/gcp"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp?ref=v0.4.2"

  project_id        = var.gcp_project_id
  invoker_sa_emails = var.worklytics_sa_emails
  bucket_location   = var.bucket_location
  psoxy_base_dir    = var.psoxy_base_dir
}



module "psoxy-gcp-bulk" {
  # source = "../../modules/gcp-psoxy-bulk"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-psoxy-bulk?ref=v0.4.2"

  project_id                    = google_project.psoxy-project.project_id
  worklytics_sa_emails          = var.worklytics_sa_emails
  region                        = var.region
  source_kind                   = var.source_kind
  salt_secret_id                = module.psoxy-gcp.salt_secret_name
  artifacts_bucket_name         = module.psoxy-gcp.artifacts_bucket_name
  deployment_bundle_object_name = module.psoxy-gcp.deployment_bundle_object_name
  path_to_config                = "${var.psoxy_base_dir}/config/${var.source_kind}.yaml"
  salt_secret_version_number    = module.psoxy-gcp.salt_secret_version_number
  psoxy_base_dir                = var.psoxy_base_dir

  depends_on = [
    google_project.psoxy-project,
  ]
}
