terraform {
  required_providers {
    # for the infra that will host Psoxy instances
    aws = {
      source  = "hashicorp/aws"
      version = "~> 4.12"
    }

    # for API connections to Microsoft 365
    azuread = {
      version = "~> 2.0"
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

provider "azuread" {
  tenant_id = var.msft_tenant_id
}

module "psoxy-aws" {
  source = "git::https://github.com/worklytics/psoxy//infra/modules/aws?ref=v0.3.0-beta.2"

  caller_aws_account_id   = var.caller_aws_account_id
  caller_external_user_id = var.caller_external_user_id
  aws_account_id          = var.aws_account_id

  providers = {
    aws = aws
  }
}

module "psoxy-package" {
  source = "git::https://github.com/worklytics/psoxy//infra/modules/psoxy-package?ref=v0.3.0-beta.2"

  implementation     = "aws"
  path_to_psoxy_java = "${var.psoxy_basedir}/java"
}

data "azuread_client_config" "current" {}

locals {
  # this IS the correct ID for the user terraform is running as, which we assume is a user who's OK
  # to use the subject of examples. You can change it to any string you want.
  example_msft_user_guid = data.azuread_client_config.current.object_id

  # Microsoft 365 sources; add/remove as you wish
  # See https://docs.microsoft.com/en-us/graph/permissions-reference for all the permissions available in AAD Graph API
  msft_sources = {
    "azure-ad" : {
      enabled : true,
      source_kind : "azure-ad",
      display_name : "Azure Directory"
      required_oauth2_permission_scopes : [], # Delegated permissions (from `az ad sp list --query "[?appDisplayName=='Microsoft Graph'].oauth2Permissions" --all`)
      required_app_roles : [                  # Application permissions (form az ad sp list --query "[?appDisplayName=='Microsoft Graph'].appRoles" --all
        "User.Read.All",
        "Group.Read.All"
      ],
      example_calls : [
        "/v1.0/users",
        "/v1.0/groups"
      ]
    },
    "outlook-cal" : {
      enabled : true,
      source_kind : "outlook-cal",
      display_name : "Outlook Calendar"
      required_oauth2_permission_scopes : [],
      required_app_roles : [
        "OnlineMeetings.Read.All",
        "Calendars.Read",
        "MailboxSettings.Read",
        "Group.Read.All",
        "User.Read.All"
      ],
      example_calls : [
        "/v1.0/users",
        "/v1.0/users/${local.example_msft_user_guid}/events",
        "/v1.0/users/${local.example_msft_user_guid}/mailboxSettings"
      ]
    },
    "outlook-mail" : {
      enabled : true,
      source_kind : "outlook-mail"
      display_name : "Outlook Mail"
      required_oauth2_permission_scopes : [],
      required_app_roles : [
        "Mail.ReadBasic.All",
        "MailboxSettings.Read",
        "Group.Read.All",
        "User.Read.All"
      ],
      example_calls : [
        "/beta/users",
        "/beta/users/${local.example_msft_user_guid}/mailboxSettings",
        "/beta/users/${local.example_msft_user_guid}/mailFolders/SentItems/messages"
      ]
    }
  }
  enabled_msft_sources = { for id, spec in local.msft_sources : id => spec if spec.enabled }
}

module "msft-connection" {
  for_each = local.enabled_msft_sources

  source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-connection?ref=v0.3.0-beta.2"

  display_name                      = "Psoxy Connector - ${each.value.display_name}${var.connector_display_name_suffix}"
  tenant_id                         = var.msft_tenant_id
  required_app_roles                = each.value.required_app_roles
  required_oauth2_permission_scopes = each.value.required_oauth2_permission_scopes
}

module "msft-connection-auth" {
  for_each = local.enabled_msft_sources

  source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-local-cert?ref=v0.3.0-beta.2"

  application_object_id = module.msft-connection[each.key].connector.id
  rotation_days         = 60
  cert_expiration_days  = 180
  certificate_subject   = var.certificate_subject
}

resource "aws_ssm_parameter" "client_id" {
  for_each = local.enabled_msft_sources

  name  = "PSOXY_${upper(replace(each.key, "-", "_"))}_CLIENT_ID"
  type  = "String"
  value = module.msft-connection[each.key].connector.application_id
}

resource "aws_ssm_parameter" "refresh_endpoint" {
  for_each = local.msft_sources

  name      = "PSOXY_${upper(replace(each.key, "-", "_"))}_REFRESH_ENDPOINT"
  type      = "String"
  overwrite = true
  value     = "https://login.microsoftonline.com/${var.msft_tenant_id}/oauth2/v2.0/token"
}


module "private-key-aws-parameters" {
  for_each = local.enabled_msft_sources

  source = "git::https://github.com/worklytics/psoxy//infra/modules/private-key-aws-parameter?ref=v0.3.0-beta.2"

  instance_id = each.key

  private_key_id = module.msft-connection-auth[each.key].private_key_id
  private_key    = module.msft-connection-auth[each.key].private_key
}

module "psoxy-msft-connector" {
  for_each = local.enabled_msft_sources

  source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-instance?ref=v0.3.0-beta.2"

  function_name        = "psoxy-${each.key}"
  source_kind          = each.value.source_kind
  path_to_function_zip = module.psoxy-package.path_to_deployment_jar
  function_zip_hash    = module.psoxy-package.deployment_package_hash
  path_to_config       = "../../../configs/${each.value.source_kind}.yaml"
  aws_assume_role_arn  = var.aws_assume_role_arn
  example_api_calls    = each.value.example_calls
  aws_account_id       = var.aws_account_id

  parameters = concat(
    module.private-key-aws-parameters[each.key].parameters,
    [
      module.psoxy-aws.salt_secret,
    ]
  )
}

# grant required permissions to connectors via Azure AD
# (requires terraform configuration being applied by an Azure User with privelleges to do this; it
#  usually requires a 'Global Administrator' for your tenant)
module "msft_365_grants" {
  for_each = local.enabled_msft_sources

  source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-grant-all-users?ref=v0.3.0-beta.2"

  application_id           = module.msft-connection[each.key].connector.application_id
  oauth2_permission_scopes = each.value.required_oauth2_permission_scopes
  app_roles                = each.value.required_app_roles
  application_name         = each.key
}


module "worklytics-psoxy-connection" {
  for_each = local.enabled_msft_sources

  source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection-aws?ref=v0.3.0-beta.2"

  psoxy_endpoint_url = module.psoxy-msft-connector[each.key].endpoint_url
  display_name       = "${each.value.display_name} via Psoxy${var.connector_display_name_suffix}"
  aws_region         = var.aws_region
  aws_role_arn       = module.psoxy-aws.api_caller_role_arn
}
