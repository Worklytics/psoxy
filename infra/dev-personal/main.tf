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

  depends_on = [
    module.psoxy-gcp
  ]
}

# setup gmail to auth using a secret
module "gmail-connector-auth" {
  source = "../modules/gcp-sa-auth-key-secret-manager"

  secret_project     = var.project_id
  service_account_id = module.gmail-connector.service_account_id
  secret_id          = "PSOXY_GMAIL_DWD_SECRET_KEY"
}


# enable local use case by writing key to flat file on your machine
resource "local_file" "gmail-connector-sa-key" {
  filename = "gmail-connector-sa-key.json"
  content_base64 = module.gmail-connector-auth.key_value
}
