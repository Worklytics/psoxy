terraform {
  required_providers {
    google = {
      version = "~> 3.74.0"
    }
  }


  # intended to be personal dev env for developer
  backend "local" {
  }
}


module "psoxy-gcp" {
  source = "../modules/gcp"

  billing_account_id  = var.billing_account_id
  environment_name    = var.environment_name
  project_id          = var.project_id
  folder_id           = var.folder_id
}
