terraform {
  required_providers {

    # for API connections to Microsoft 365
    azuread = {
      version = "~> 2.0"
    }
  }

  # we recommend you use a secure location for your Terraform state (such as S3 bucket), as it
  # may contain sensitive values (such as API keys) depending on which data sources you configure.
  #
  # local may be safe for production-use IFF you are executing Terraform from a secure location
  #
  # Please review and seek guidance from your Security team if in doubt.
  backend "local" {
  }

  # example remove backend (this S3 bucket must already be provisioned, and AWS role executing
  # terraform must be able to read/write to it - and use encryption key, if any)
  #  backend "s3" {
  #    bucket = "mybucket"
  #    key    = "path/to/my/key"
  #    region = "us-east-1"
  #  }
}


provider "azuread" {
  tenant_id = var.msft_tenant_id
}

data "azuread_client_config" "current" {}

module "worklytics_connector_specs" {
  # source = "../../modules/worklytics-connector-specs"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-connector-specs?ref=v0.4.57"

  enabled_connectors = var.enabled_connectors

  # this IS the correct ID for the user terraform is running as, which we assume is a user who's OK
  # to use the subject of examples. You can change it to any string you want.
  example_msft_user_guid = data.azuread_client_config.current.object_id
}

locals {
  base_config_path = "${var.psoxy_base_dir}configs/"

}

module "msft-connection" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  # source = "../../modules/azuread-connection"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-connection?ref=v0.4.57"

  display_name                      = "Psoxy Connector - ${each.value.display_name}${var.connector_display_name_suffix}"
  tenant_id                         = var.msft_tenant_id
  required_app_roles                = each.value.required_app_roles
  required_oauth2_permission_scopes = each.value.required_oauth2_permission_scopes
}


module "msft-connection-auth-federation" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  # source = "../../modules/azuread-federated-credentials"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-federated-credentials?ref=v0.4.57"

  application_object_id = module.msft-connection[each.key].connector.id
  display_name          = "AccessFromAWS"
  description           = "AWS federation to be used for psoxy Connector - ${each.value.display_name}${var.connector_display_name_suffix}"
  issuer                = "https://cognito-identity.amazonaws.com"
  audience              = var.cognito_pool_id
  subject               = var.cognito_connector_identities[each.key]
}



resource "local_file" "configure_client_id" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors


  # TODO: CLIENT_ID, REFRESH_ENDPOINT better as env variables
  filename = "TODO 1 - setup ${each.key} secrets in AWS (SENSITIVE).md"
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
  source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-grant-all-users?ref=v0.4.57"

  psoxy_instance_id        = each.key
  application_id           = module.msft-connection[each.key].connector.application_id
  oauth2_permission_scopes = each.value.required_oauth2_permission_scopes
  app_roles                = each.value.required_app_roles
  application_name         = each.key
  todo_step                = 2
}
