terraform {
  required_providers {
    google = {
      version = "4.12.0"
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
module "psoxy-gcp-bulk" {
  source                  = "../../modules/gcp-bulk"

  project_id   = var.project_id
  environment_name = var.environment_name
  folder_id          = var.folder_id
  billing_account_id     = var.billing_account_id
  worklytics_sa_emails             = var.worklytics_sa_emails
  region             = var.region
  bucket_prefix              = var.bucket_prefix
  bucket_location              = var.bucket_location
}