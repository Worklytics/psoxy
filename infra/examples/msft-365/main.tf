terraform {
  required_providers {

    # for API connections to Microsoft 365
    azuread = {
      version = "~> 2.0"
    }
  }

  # if you leave this as local, you should backup/commit your TF state files
  backend "local" {
  }
}


provider "azuread" {
  tenant_id = var.msft_tenant_id
}

data "azuread_client_config" "current" {}

module "worklytics_connector_specs" {
  source = "../../modules/worklytics-connector-specs"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-connector-specs?ref=v0.4.1"

  enabled_connectors = [
    "azure-ad",
    "outlook-cal",
    "outlook-mail",
    "asana",
    "slack-discovery-api",
    "zoom",
  ]

  # this IS the correct ID for the user terraform is running as, which we assume is a user who's OK
  # to use the subject of examples. You can change it to any string you want.
  example_msft_user_guid = data.azuread_client_config.current.object_id
}

locals {
  base_config_path       = "${var.psoxy_base_dir}configs/"

}

module "msft-connection" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  # source = "../../modules/azuread-connection"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-connection?ref=v0.4.1"

  display_name                      = "Psoxy Connector - ${each.value.display_name}${var.connector_display_name_suffix}"
  tenant_id                         = var.msft_tenant_id
  required_app_roles                = each.value.required_app_roles
  required_oauth2_permission_scopes = each.value.required_oauth2_permission_scopes
}


# if you don't want Terraform to generate certificate for you on your local machine, comment this
# out
module "msft-connection-auth" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  # source = "../../modules/azuread-local-cert"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-local-cert?ref=v0.4.1"

  application_object_id = module.msft-connection[each.key].connector.id
  rotation_days         = 60
  cert_expiration_days  = 180
  certificate_subject   = var.certificate_subject
}



resource "local_file" "configure_client_id" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  filename = "TODO - SENSITIVE - setup ${each.key} secrets in AWS.md"
  content  = <<EOT

  1. Set `PSOXY_${upper(replace(each.key, "-", "_"))}_CLIENT_ID` as an AWS SSM Parameter to value
     `${module.msft-connection[each.key].connector.application_id}`
  2. Set `PSOXY_${upper(replace(each.key, "-", "_"))}_REFRESH_ENDPOINT` as an AWS SSM Parameter to
     `https://login.microsoftonline.com/${var.msft_tenant_id}/oauth2/v2.0/token`
  3. Set `PSOXY_${upper(replace(each.key, "-", "_"))}_PRIVATE_KEY_ID` as an AWS SSM Parameter to
     `module.msft-connection-auth[each.key].private_key_id`
  4. Set `PSOXY_${upper(replace(each.key, "-", "_"))}_PRIVATE_KEY` as an AWS SSM Parameter to
     `module.msft-connection-auth[each.key].private_key`

EOT
}


# grant required permissions to connectors via Azure AD
# (requires terraform configuration being applied by an Azure User with privileges to do this; it
#  usually requires a 'Global Administrator' for your tenant)
# NOTE: you can comment this out, but then will have to figure out how to do this on your own
# outside of Terraform (eg, navigate the Azure AD console and find the apps in question)
module "msft_365_grants" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  # source = "../../modules/azuread-grant-all-users"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-grant-all-users?ref=v0.4.1"

  application_id           = module.msft-connection[each.key].connector.application_id
  oauth2_permission_scopes = each.value.required_oauth2_permission_scopes
  app_roles                = each.value.required_app_roles
  application_name         = each.key
}
