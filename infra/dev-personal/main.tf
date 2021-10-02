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

# setup gmail to auth using a secret (not specific to Cloud Function
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


# enable local use case by writing key to flat file on your machine
resource "local_file" "gmail-connector-sa-key" {
  filename = "gmail-connector-sa-key.json"
  content_base64 = module.gmail-connector-auth.key_value
}
