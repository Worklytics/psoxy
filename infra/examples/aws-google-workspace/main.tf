terraform {
  required_providers {
    # for the infra that will host Psoxy instances
    aws = {
      source  = "hashicorp/aws"
      version = "~> 3.0"
    }

    # for the API connections to Google Workspace
    google = {
      version = ">= 3.74, <= 4.0"
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

module "psoxy-aws" {
  source = "../../modules/aws"
  caller_aws_account_id = var.caller_aws_account_id
  caller_aws_user_id    = var.caller_aws_user_id
  aws_account_id        = var.aws_account_id

  providers = {
    aws = aws
  }

}

# holds SAs + keys needed to connect to Google Workspace APIs
resource "google_project" "psoxy-google-connectors" {
  name            = "Psoxy Connectors - ${var.environment_name}"
  project_id      = var.gcp_project_id
  folder_id       = var.gcp_folder_id
  billing_account = var.gcp_billing_account_id
}

locals {
  # Google Workspace Sources; add/remove as you wish
  google_workspace_sources = {
    # GDirectory connections are a PRE-REQ for gmail, gdrive, and gcal connections. remove only
    # if you plan to directly connect Directory to worklytics (without proxy). such a scenario is
    # used for customers who care primarily about pseudonymizing PII of external subjects with whom
    # they collaborate in GMail/GCal/Gdrive. the Directory does not contain PII of subjects external
    # to the Google Workspace, so may be directly connected in such scenarios.
    "gdirectory": {
      display_name: "Google Directory"
      apis_consumed: [
        "admin.googleapis.com"
      ]
      oauth_scopes_needed: [
        "https://www.googleapis.com/auth/admin.directory.user.readonly",
        "https://www.googleapis.com/auth/admin.directory.user.alias.readonly",
        "https://www.googleapis.com/auth/admin.directory.domain.readonly",
        "https://www.googleapis.com/auth/admin.directory.group.readonly",
        "https://www.googleapis.com/auth/admin.directory.group.member.readonly",
        "https://www.googleapis.com/auth/admin.directory.orgunit.readonly",
        "https://www.googleapis.com/auth/admin.directory.rolemanagement.readonly"
      ],
      worklytics_connector_name: "Google Workspace Directory via Psoxy"
    }
    "gcal": {
      display_name: "Google Calendar"
      apis_consumed: [
        "calendar-json.googleapis.com"
      ]
      oauth_scopes_needed: [
        "https://www.googleapis.com/auth/calendar.readonly"
      ]
    }
    "gmail": {
      display_name: "GMail"
      apis_consumed: [
        "gmail.googleapis.com"
      ]
      oauth_scopes_needed: [
        "https://www.googleapis.com/auth/gmail.metadata"
      ]
    }
    "google-chat": {
      display_name: "Google Chat"
      apis_consumed: [
        "admin.googleapis.com"
      ]
      oauth_scopes_needed: [
        "https://www.googleapis.com/auth/admin.reports.audit.readonly"
      ]
    }
    "gdrive": {
      display_name: "Google Drive"
      apis_consumed: [
        "drive.googleapis.com"
      ]
      oauth_scopes_needed: [
        "https://www.googleapis.com/auth/drive.metadata.readonly"
      ]
    }
    "google-meet": {
      display_name: "Google Meet"
      apis_consumed: [
        "admin.googleapis.com"
      ]
      oauth_scopes_needed: [
        "https://www.googleapis.com/auth/admin.reports.audit.readonly"
      ]
    }
  }
}

module "google-workspace-connection" {
  for_each = local.google_workspace_sources

  source = "../../modules/google-workspace-dwd-connection"

  project_id                   = google_project.psoxy-google-connectors.project_id
  connector_service_account_id = "psoxy-${each.key}-dwd"
  display_name                 = "Psoxy Connector - ${each.value.display_name}${var.connector_display_name_suffix}"
  apis_consumed                = each.value.apis_consumed
  oauth_scopes_needed          = each.value.oauth_scopes_needed

  depends_on = [
    module.psoxy-aws,
    google_project.psoxy-google-connectors
  ]
}

module "google-workspace-connection-auth" {
  for_each = local.google_workspace_sources

  source = "../../modules/gcp-sa-auth-key-aws-secret"

  service_account_id = module.google-workspace-connection[each.key].service_account_id
  secret_id          = "PSOXY_${upper(each.key)}_SERVICE_ACCOUNT_KEY"
}

module "psoxy-google-workspace-connector" {
  for_each = local.google_workspace_sources

  source = "../../modules/aws-psoxy-instance"

  function_name        = "psoxy-${each.key}"
  source_kind          = each.key
  api_gateway          = module.psoxy-aws.api_gateway
  path_to_function_zip = "../../../java/impl/aws/target/psoxy-aws-1.0-SNAPSHOT.jar"
  path_to_config       = "../../../configs/${each.key}.yaml"
  api_caller_role_arn  = module.psoxy-aws.api_caller_role_arn
  aws_assume_role_arn  = var.aws_assume_role_arn

  parameter_bindings   = {
    PSOXY_SALT          = module.psoxy-aws.salt_secret
    SERVICE_ACCOUNT_KEY = module.google-workspace-connection-auth[each.key].key_secret
  }
}

module "worklytics-psoxy-connection" {
  for_each = local.google_workspace_sources

  source = "../../modules/worklytics-psoxy-connection"

  psoxy_endpoint_url = module.psoxy-google-workspace-connector[each.key].endpoint_url
  display_name       = "${each.value.display_name} via Psoxy"
}
