terraform {
  required_providers {
    google = {
      version = ">= 3.74, <= 4.0"
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
  name            = "Worklytics Psoxy%{if var.environment_name != ""} - ${var.environment_name}%{endif}"
  project_id      = var.gcp_project_id
  billing_account = var.gcp_billing_account_id
  folder_id       = var.gcp_folder_id # if project is at top-level of your GCP organization, rather than in a folder, comment this line out
  # org_id          = var.gcp_org_id # if project is in a GCP folder, this value is implicit and this line should be commented out
}

module "psoxy-gcp" {
  source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp?ref=v0.3.0-beta.2"

  project_id        = google_project.psoxy-project.project_id
  invoker_sa_emails = var.worklytics_sa_emails

  depends_on = [
    google_project.psoxy-project
  ]
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
      ],
      worklytics_connector_name : "Google Workspace Directory via Psoxy"
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
}

module "google-workspace-connection" {
  for_each = local.enabled_google_workspace_sources

  source = "git::https://github.com/worklytics/psoxy//infra/modules/google-workspace-dwd-connection?ref=v0.3.0-beta.2"

  project_id                   = google_project.psoxy-project.project_id
  connector_service_account_id = "psoxy-${each.key}-dwd"
  display_name                 = "Psoxy Connector - ${each.value.display_name}${var.connector_display_name_suffix}"
  apis_consumed                = each.value.apis_consumed
  oauth_scopes_needed          = each.value.oauth_scopes_needed

  depends_on = [
    module.psoxy-gcp
  ]
}

module "google-workspace-connection-auth" {
  for_each = local.enabled_google_workspace_sources

  source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-sa-auth-key-secret-manager?ref=v0.3.0-beta.2"

  secret_project     = google_project.psoxy-project.project_id
  service_account_id = module.google-workspace-connection[each.key].service_account_id
  secret_id          = "PSOXY_${replace(upper(each.key), "-", "_")}_SERVICE_ACCOUNT_KEY"
}

module "psoxy-google-workspace-connector" {
  for_each = local.enabled_google_workspace_sources

  source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-psoxy-cloud-function?ref=v0.3.0-beta.2"

  project_id            = google_project.psoxy-project.project_id
  function_name         = "psoxy-${each.key}"
  source_kind           = each.value.source_kind
  service_account_email = module.google-workspace-connection[each.key].service_account_email

  secret_bindings = {
    PSOXY_SALT = {
      secret_name    = module.psoxy-gcp.salt_secret_name
      version_number = module.psoxy-gcp.salt_secret_version_number
    },
    SERVICE_ACCOUNT_KEY = {
      secret_name    = module.google-workspace-connection-auth[each.key].key_secret_name
      version_number = module.google-workspace-connection-auth[each.key].key_secret_version_number
    }
  }
}

module "worklytics-psoxy-connection" {
  for_each = local.enabled_google_workspace_sources

  source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection?ref=v0.3.0-beta.2"

  psoxy_endpoint_url = module.psoxy-google-workspace-connector[each.key].cloud_function_url
  display_name       = "${each.value.display_name} via Psoxy"
}
