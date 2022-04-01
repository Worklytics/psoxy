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
  source  = "terraform-google-modules/bootstrap/google"
  version = "~> 4.0.0"

  org_id                     = var.org_id
  billing_account            = var.billing_account
  group_org_admins           = var.group_org_admins
  group_billing_admins       = var.group_billing_admins
  default_region             = var.default_region
  encrypt_gcs_bucket_tfstate = true # just recommended, but change if you want ...
}

resource "local_file" "todo" {
  filename = "TODO - terraform backend.md"
  content  = <<EOT
Ensure the `terraform` block at the top of your Terraform configuration is something like following:

```terraform
terraform {
  required_providers {
    google = {
      version = "~> 4.0.0"
    }
  }
  backend "gcs" {
    bucket = "${module.bootstrap.gcs_bucket_tfstate}"
    prefix = "terraform_state"
    impersonate_service_account = "${module.bootstrap.terraform_sa_email}"
  }
}

provider "google" {
  project                     = "${module.bootstrap.seed_project_id}
  impersonate_service_account = "${module.bootstrap.terraform_sa_email}"
}
```
EOT
}
