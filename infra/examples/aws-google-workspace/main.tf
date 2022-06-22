terraform {
  required_providers {
    # for the infra that will host Psoxy instances
    aws = {
      source  = "hashicorp/aws"
      version = "~> 4.12"
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
  source = "git::https://github.com/worklytics/psoxy//infra/modules/aws?ref=v0.3.0-beta.5"

  caller_aws_account_id   = var.caller_aws_account_id
  caller_external_user_id = var.caller_external_user_id
  aws_account_id          = var.aws_account_id
}

module "psoxy-package" {
  source = "git::https://github.com/worklytics/psoxy//infra/modules/psoxy-package?ref=v0.3.0-beta.5"

  implementation     = "aws"
  path_to_psoxy_java = "${var.psoxy_base_dir}/java"
}

# holds SAs + keys needed to connect to Google Workspace APIs
resource "google_project" "psoxy-google-connectors" {
  name            = "Worklytics Connectors%{if var.environment_name != ""} - ${var.environment_name}%{endif}"
  project_id      = var.gcp_project_id
  billing_account = var.gcp_billing_account_id
  folder_id       = var.gcp_folder_id # if project is at top-level of your GCP organization, rather than in a folder, comment this line out
  # org_id          = var.gcp_org_id # if project is in a GCP folder, this value is implicit and this line should be commented out
}

locals {
  # Google Workspace Sources; add/remove as you wish, or toggle 'enabled' flag
  google_workspace_sources = {
    # GDirectory connections are a PRE-REQ for gmail, gdrive, and gcal connections. remove only
    # if you plan to directly connect Directory to worklytics (without proxy). such a scenario is
    # used for customers who care primarily about pseudonymizing PII of external subjects with whom
    # they collaborate in GMail/GCal/Gdrive. the Directory does not contain PII of subjects external
    # to the Google Workspace, so may be directly connected in such scenarios.
    "gdirectory" : {
      enabled : true,
      source_kind : "gdirectory",
      display_name : "Google Directory"
      apis_consumed : [
        "admin.googleapis.com"
      ]
      oauth_scopes_needed : [
        "https://www.googleapis.com/auth/admin.directory.user.readonly",
        "https://www.googleapis.com/auth/admin.directory.user.alias.readonly",
        "https://www.googleapis.com/auth/admin.directory.domain.readonly",
        "https://www.googleapis.com/auth/admin.directory.group.readonly",
        "https://www.googleapis.com/auth/admin.directory.group.member.readonly",
        "https://www.googleapis.com/auth/admin.directory.orgunit.readonly",
        "https://www.googleapis.com/auth/admin.directory.rolemanagement.readonly"
      ]
    }
    "gcal" : {
      enabled : true,
      source_kind : "gcal",
      display_name : "Google Calendar"
      apis_consumed : [
        "calendar-json.googleapis.com"
      ]
      oauth_scopes_needed : [
        "https://www.googleapis.com/auth/calendar.readonly"
      ]
    }
    "gmail" : {
      enabled : true,
      source_kind : "gmail",
      display_name : "GMail"
      apis_consumed : [
        "gmail.googleapis.com"
      ]
      oauth_scopes_needed : [
        "https://www.googleapis.com/auth/gmail.metadata"
      ]
    }
    "google-chat" : {
      enabled : true,
      source_kind : "google-chat",
      display_name : "Google Chat"
      apis_consumed : [
        "admin.googleapis.com"
      ]
      oauth_scopes_needed : [
        "https://www.googleapis.com/auth/admin.reports.audit.readonly"
      ]
    }
    "gdrive" : {
      enabled : true,
      source_kind : "gdrive",
      display_name : "Google Drive"
      apis_consumed : [
        "drive.googleapis.com"
      ]
      oauth_scopes_needed : [
        "https://www.googleapis.com/auth/drive.metadata.readonly"
      ]
    }
    "google-meet" : {
      enabled : true,
      source_kind : "google-meet",
      display_name : "Google Meet"
      apis_consumed : [
        "admin.googleapis.com"
      ]
      oauth_scopes_needed : [
        "https://www.googleapis.com/auth/admin.reports.audit.readonly"
      ]
    }
  }
  enabled_google_workspace_sources = { for id, spec in local.google_workspace_sources : id => spec if spec.enabled }
  base_config_path                 = "${var.psoxy_base_dir}/configs/"
}

module "google-workspace-connection" {
  for_each = local.enabled_google_workspace_sources

  source = "git::https://github.com/worklytics/psoxy//infra/modules/google-workspace-dwd-connection?ref=v0.3.0-beta.5"

  project_id                   = google_project.psoxy-google-connectors.project_id
  connector_service_account_id = "psoxy-${each.key}"
  display_name                 = "Psoxy Connector - ${each.value.display_name}${var.connector_display_name_suffix}"
  apis_consumed                = each.value.apis_consumed
  oauth_scopes_needed          = each.value.oauth_scopes_needed

  depends_on = [
    module.psoxy-aws,
    google_project.psoxy-google-connectors
  ]
}

module "google-workspace-connection-auth" {
  for_each = local.enabled_google_workspace_sources

  source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-sa-auth-key-aws-secret?ref=v0.3.0-beta.5"

  service_account_id = module.google-workspace-connection[each.key].service_account_id
  secret_id          = "PSOXY_${replace(upper(each.key), "-", "_")}_SERVICE_ACCOUNT_KEY"
}

module "psoxy-google-workspace-connector" {
  for_each = local.enabled_google_workspace_sources

  source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-instance?ref=v0.3.0-beta.5"

  function_name        = "psoxy-${each.key}"
  source_kind          = each.key
  path_to_function_zip = module.psoxy-package.path_to_deployment_jar
  function_zip_hash    = module.psoxy-package.deployment_package_hash
  path_to_config       = "${local.base_config_path}/${each.key}.yaml"
  aws_assume_role_arn  = var.aws_assume_role_arn
  aws_account_id       = var.aws_account_id
  # from next version
  #path_to_repo_root    = var.proxy_base_dir

  parameters = [
    module.psoxy-aws.salt_secret,
    module.google-workspace-connection-auth[each.key].key_secret
  ]
  example_api_calls = []
}


module "worklytics-psoxy-connection-google-workspace" {
  for_each = local.enabled_google_workspace_sources

  source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection-aws?ref=v0.3.0-beta.5"

  psoxy_endpoint_url = module.psoxy-google-workspace-connector[each.key].endpoint_url
  display_name       = "${each.value.display_name} via Psoxy${var.connector_display_name_suffix}"
  aws_region         = var.aws_region
  aws_role_arn       = module.psoxy-aws.api_caller_role_arn
}
