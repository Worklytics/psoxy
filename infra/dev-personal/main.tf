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


module "gmail-connector" {
  source = "../modules/google-workspace-dwd-connection"

  project_id                   = var.project_id
  connector_service_account_id = "psoxy-gmail-dwd"
  display_name                 = "Psoxy Connector - GMail Dev Erik"
  apis_consumed                = [
    "gmail.googleapis.com",
    # TODO: probably directory too!?!?
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
  secret_id          = "PSOXY_SERVICE_ACCOUNT_KEY_gmail"
}

module "gmail-access-secret" {
  source = "../modules/gcp-secret-to-cloud-function"

  secret_name           = module.gmail-connector-auth.key_secret_name
  secret_version_name   = module.gmail-connector-auth.key_secret_version_name
  service_account_email = module.gmail-connector.service_account_email
  project_id            = var.project_id
  function_name         = "psoxy-gmail"
}

module "gmail-access-salt" {
  source = "../modules/gcp-secret-to-cloud-function"

  secret_name           = module.psoxy-gcp.salt_secret_name
  secret_version_name   = module.psoxy-gcp.salt_secret_version_name
  service_account_email = module.gmail-connector.service_account_email
  project_id            = var.project_id
  function_name         = "psoxy-gmail"
}

# enable local use case by writing key to flat file on your machine
resource "local_file" "gmail-connector-sa-key" {
  filename = "gmail-connector-sa-key.json"
  content_base64 = module.gmail-connector-auth.key_value
}


module "google-chat-connector" {
  source = "../modules/google-workspace-dwd-connection"

  project_id                   = var.project_id
  connector_service_account_id = "psoxy-google-chat-dwd"
  display_name                 = "Psoxy Connector - Google Chat Dev Erik"
  apis_consumed                = [
    "admin.googleapis.com",
    # TODO: probably directory too!?!?
  ]

  depends_on = [
    module.psoxy-gcp
  ]
}

module "google-chat-connector-auth" {
  source = "../modules/gcp-sa-auth-key-secret-manager"

  secret_project     = var.project_id
  service_account_id = module.google-chat-connector.service_account_id
  secret_id          = "PSOXY_SERVICE_ACCOUNT_KEY_google-chat"
}


module "google-chat-access-sa-key-secret" {
  source = "../modules/gcp-secret-to-cloud-function"

  secret_name           = module.google-chat-connector-auth.key_secret_name
  secret_version_name   = module.google-chat-connector-auth.key_secret_version_name
  service_account_email = module.google-chat-connector.service_account_email
  project_id            = var.project_id
  function_name         = "psoxy-google-chat"
}

module "google-chat-access-salt" {
source = "../modules/gcp-secret-to-cloud-function"

function_name = "psoxy-google-chat"
project_id = var.project_id
secret_name = module.psoxy-gcp.salt_secret_name
secret_version_name = module.psoxy-gcp.salt_secret_version_name
service_account_email = module.google-chat-connector.service_account_email
}


# grants invoker to worklytics on ALL functions in this project. this is the recommended setup, as
# we expect this GCP project to only be used of psoxy instances to be consumed from your Worklytics
# account; otherwise, you can grant this role on specific functions
resource "google_project_iam_member" "grant_cloudFunctionInvoker_to_worklytics" {
  for_each = toset(var.worklytics_sa_emails)

  project = var.project_id
  member  = "serviceAccount:${each.value}"
  role    = "roles/cloudfunctions.invoker"
}
