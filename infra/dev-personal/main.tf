terraform {
  required_providers {
    google = {
      version = "~> 4.0.0"
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
  name            = "Psoxy - ${var.environment_name}"
  project_id      = var.project_id
  folder_id       = var.folder_id
  billing_account = var.billing_account_id
}

module "psoxy-gcp" {
  source = "../modules/gcp"

  project_id          = google_project.psoxy-project.project_id
  invoker_sa_emails   = var.worklytics_sa_emails

  depends_on = [
    google_project.psoxy-project
  ]
}

module "gmail-connector" {
  source = "../modules/google-workspace-dwd-connection"

  project_id                   = var.project_id
  connector_service_account_id = "psoxy-gmail-dwd"
  display_name                 = "Psoxy Connector - GMail${var.connector_display_name_suffix}"
  apis_consumed                = [
    "gmail.googleapis.com"
  ]

  depends_on = [
    module.psoxy-gcp
  ]
}

# setup gmail to auth using a secret (not specific to Cloud Function)
module "gmail-connector-auth" {
  source = "../modules/gcp-sa-auth-key-secret-manager"

  secret_project     = var.project_id
  service_account_id = module.gmail-connector.service_account_id

  # TODO: recommend migrate this to `PSOXY_{{function_name}}_SERVICE_ACCOUNT_KEY`
  secret_id          = "PSOXY_SERVICE_ACCOUNT_KEY_gmail"
}

# for local dev, write the key to flat file on your machine (shouldn't do this for prod configs)
resource "local_file" "gmail-connector-sa-key" {
  filename = "gmail-connector-sa-key.json"
  content_base64 = module.gmail-connector-auth.key_value
}

module "psoxy-gmail" {
  source = "../modules/gcp-psoxy-cloud-function"

  project_id            = var.project_id
  function_name         = "psoxy-gmail"
  source_kind           = "gmail"
  service_account_email = module.gmail-connector.service_account_email

  secret_bindings = {
    PSOXY_SALT = {
      secret_name    = module.psoxy-gcp.salt_secret_name
      version_number = module.psoxy-gcp.salt_secret_version_number
    },
    SERVICE_ACCOUNT_KEY = {
      secret_name    = module.gmail-connector-auth.key_secret_name
      version_number = module.gmail-connector-auth.key_secret_version_number
    }
  }
}

module "google-chat-connector" {
  source = "../modules/google-workspace-dwd-connection"

  project_id                   = var.project_id
  connector_service_account_id = "psoxy-google-chat-dwd"
  display_name                 = "Psoxy Connector - Google Chat${var.connector_display_name_suffix}"
  apis_consumed                = [
    "admin.googleapis.com"
  ]

  depends_on = [
    module.psoxy-gcp
  ]
}

module "google-chat-connector-auth" {
  source = "../modules/gcp-sa-auth-key-secret-manager"

  secret_project     = var.project_id
  service_account_id = module.google-chat-connector.service_account_id
  # TODO: recommend migrate this to `PSOXY_{{function_name}}_SERVICE_ACCOUNT_KEY`
  secret_id          = "PSOXY_SERVICE_ACCOUNT_KEY_google-chat"
}

module "psoxy-google-chat" {
  source = "../modules/gcp-psoxy-cloud-function"

  project_id            = var.project_id
  function_name         = "psoxy-google-chat"
  source_kind           = "google-chat"
  service_account_email = module.google-chat-connector.service_account_email

  secret_bindings       = {
    PSOXY_SALT = {
      secret_name    = module.psoxy-gcp.salt_secret_name
      version_number = module.psoxy-gcp.salt_secret_version_number
    },
    SERVICE_ACCOUNT_KEY = {
      secret_name    = module.google-chat-connector-auth.key_secret_name
      version_number = module.google-chat-connector-auth.key_secret_version_number
    }
  }
}

locals {
  # set this to no if it's the first time creating the slack function
  slack_secret_created  = "no"
  slack_service_account = "psoxy-slack-discovery-api@psoxy-dev-jose.iam.gserviceaccount.com"
  slack_function_name   = "psoxy-slack-discovery-api"
}

# creates the secret, without versions. Only if local.slack_secret_created is set to "yes"
module "slack-discovery-api-auth" {
  source   = "../modules/gcp-oauth-long-access-strategy"

  count = local.slack_secret_created == "yes" ? 0 : 1
  project_id              = var.project_id
  function_name           = local.slack_function_name
  token_adder_user_emails = []
}

# reads the secret reference to bind it to slack connector cloud function
module "slack-discovery-api-auth-read" {
  source   = "../modules/gcp-oauth-long-access-strategy-read"

  project_id              = var.project_id
  function_name           = local.slack_function_name

  depends_on = [
    module.slack-discovery-api-auth.access_token_secret_name
  ]
}

module "psoxy-slack-discovery-api" {
  source = "../modules/gcp-psoxy-cloud-function"

  project_id            = var.project_id
  function_name         = local.slack_function_name
  source_kind           = "slack"
  service_account_email   = local.slack_service_account

  secret_bindings = {
    PSOXY_SALT   = {
      secret_name    = module.psoxy-gcp.salt_secret_name
      version_number = module.psoxy-gcp.salt_secret_version_number
    },
    ACCESS_TOKEN = {
      secret_name    = module.slack-discovery-api-auth-read.access_token_secret_name
      # in case of long lived tokens we want latest version always
      version_number = "latest"
    }
  }
}
