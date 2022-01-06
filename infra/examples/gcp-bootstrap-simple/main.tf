terraform {
  required_providers {
    google = {
      version = ">= 3.74, <= 4.0"
    }
  }

  backend "local" {
  }
}

# see: https://registry.terraform.io/modules/terraform-google-modules/bootstrap/google/latest
module "bootstrap" {
  source  = "../modules/gcp-bootstrap"

  project_id            = var.project_id
  project_name          = var.project_name
  bucket_labels         = var.bucket_labels
  project_labels        = var.project_labels
  kms_resource_location = var.kms_resource_location
  storage_location      = var.storage_location
}
