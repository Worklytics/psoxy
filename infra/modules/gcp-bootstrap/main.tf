
resource "google_project" "project" {
  name       = coalesce(var.project_name, var.project_id)
  project_id = var.project_id
  labels     = var.project_labels
}

resource "google_kms_key_ring" "ring" {
  project  = var.project_id
  name     = "keys"
  location = var.kms_resource_location
}

resource "google_kms_crypto_key" "terraform-state" {
  key_ring = google_kms_key_ring.ring.id
  name     = "terraform-state-key"
}

resource "google_storage_bucket" "state_bucket" {
  name                        = "psoxy-terraform-state"
  uniform_bucket_level_access = true # good practice
  labels                      = var.bucket_labels
  location                    = var.storage_location

  encryption {
    default_kms_key_name = google_kms_crypto_key.terraform-state.name
  }
}

locals {
  todo_content = <<EOT
Ensure the `terraform` block at the top of your Terraform configuration is something like following:
```terraform
terraform {
  required_providers {
    google = {
      version = "~> 4.0.0"
    }
  }
  backend "gcs" {
    bucket = "${google_storage_bucket.state_bucket.name}"
    prefix = "terraform_state"
  }
}
```
EOT
}

resource "local_file" "todo" {
  filename = "TODO - terraform backend.md"
  content  = local.todo_content
}

output "todo" {
  value       = local.todo_content
  description = "Markdown-formatted TODO list."
}

# NOTE: outputs aligned to https://registry.terraform.io/modules/terraform-google-modules/bootstrap/google/latest
output "gcs_bucket_tfstate" {
  value       = google_storage_bucket.state_bucket.name
  description = "GCS bucket to be used to persist Terraform state."
}
