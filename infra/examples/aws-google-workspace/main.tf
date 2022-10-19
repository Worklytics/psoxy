# example configuration of Psoxy deployment for Google Workspace-based organization into AWS

terraform {
  required_providers {
    # for the infra that will host Psoxy instances
    aws = {
      source  = "hashicorp/aws"
      version = "~> 4.12"
    }

    # for the API connections to Google Workspace
    google = {
      version = ">= 3.74, <= 5.0"
    }
  }

  # if you leave this as local, you should backup/commit your TF state files
  backend "local" {
  }
}

# NOTE: you need to provide credentials. usual way to do this is to set env vars:
#        AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
# see https://registry.terraform.io/providers/hashicorp/aws/latest/docs#authentication for more
# information as well as alternative auth approaches
provider "aws" {
  region = var.aws_region

  assume_role {
    role_arn = var.aws_assume_role_arn
  }
  allowed_account_ids = [
    var.aws_account_id
  ]
}

# holds SAs + keys needed to connect to Google Workspace APIs
resource "google_project" "psoxy-google-connectors" {
  name            = "Worklytics Connect%{if var.environment_name != ""} - ${var.environment_name}%{endif}"
  project_id      = var.gcp_project_id
  billing_account = var.gcp_billing_account_id
  folder_id       = var.gcp_folder_id # if project is at top-level of your GCP organization, rather than in a folder, comment this line out
  # org_id          = var.gcp_org_id # if project is in a GCP folder, this value is implicit and this line should be commented out

  # NOTE: these are provide because OFTEN customers have pre-existing GCP project; if such, there's
  # usually no need to specify folder_id/org_id/billing_account and have changes applied
  lifecycle {
    ignore_changes = [
      org_id,
      folder_id,
      billing_account,
    ]
  }
}

module "aws-google-workspace" {
  source = "../../modules/aws-google-workspace"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-google-workspace?ref=v0.4.8"

  aws_account_id                 = var.aws_account_id
  aws_assume_role_arn            = var.aws_assume_role_arn # role that can test the instances (lambdas)
  gcp_project_id                 = google_project.psoxy-google-connectors.project_id
  environment_name               = var.environment_name
  psoxy_base_dir                 = var.psoxy_base_dir
  google_workspace_example_user  = var.google_workspace_example_user
  caller_aws_arns                = var. caller_aws_arns
  caller_gcp_service_account_ids = var.caller_gcp_service_account_ids
  enabled_connectors             = var.enabled_connectors
  non_production_connectors      = var.non_production_connectors

  depends_on = [
    google_project.psoxy-google-connectors
  ]
}
