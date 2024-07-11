# BEGIN MSFT

module "worklytics_connectors_msft_365" {
  source = "../../modules/worklytics-connectors-msft-365"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-connectors-msft-365?ref=v0.4.57"

  enabled_connectors                  = var.enabled_connectors
  environment_id                      = var.environment_name
  msft_tenant_id                      = var.msft_tenant_id
  example_msft_user_guid              = var.example_msft_user_guid
  msft_owners_email                   = var.msft_owners_email
  msft_teams_example_team_guid        = var.msft_teams_example_team_guid
  msft_teams_example_channel_guid     = var.msft_teams_example_channel_guid
  msft_teams_example_chat_guid        = var.msft_teams_example_chat_guid
  msft_teams_example_call_guid        = var.msft_teams_example_call_guid
  msft_teams_example_call_record_guid = var.msft_teams_example_call_record_guid
  msft_connector_app_object_id        = var.msft_connector_app_object_id
  todos_as_local_files                = var.todos_as_local_files
  todo_step                           = 1
}

provider "azuread" {
  tenant_id = var.msft_tenant_id
}

locals {
  env_qualifier           = coalesce(var.environment_name, "psoxy")
  msft_365_enabled        = length(module.worklytics_connectors_msft_365.enabled_api_connectors) > 0
  developer_provider_name = "${local.env_qualifier}-azure-access"
}

# BEGIN MSFT AUTH
# q: better to extract this into module?
#   - as this is a 'root' Terraform configuration, it will be 1 rather than 3 clones of git repos,
#     and 1 rather than 3 places to change version numbers
#   - raises level of abstraction, but not very "flat" Terraform style
#   - but given that may be swapped out for certificate-based auth, raising level of abstraction
#  seems like a good idea; this module shouldn't know *details* of aws-msft-auth-identity-federation
#  vs aws-msft-auth-certificate right?
#  --> although there is a difference that one fills ENV vars, and other secrets

data "aws_region" "current" {

}

module "cognito_identity_pool" {
  count = local.msft_365_enabled ? 1 : 0 # only provision identity pool if MSFT-365 connectors are enabled

  source = "../../modules/aws-cognito-pool"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-cognito-pool?ref=v0.4.57"

  developer_provider_name = local.developer_provider_name
  name                    = "${local.env_qualifier}-azure-ad-federation"
}

locals {

  provision_entraid_apps = var.msft_connector_app_object_id == null
  # either ONE shared, or ONE per connector
  shared_connector = local.provision_entraid_apps ? null : module.worklytics_connectors_msft_365.enabled_api_connectors[keys(module.worklytics_connectors_msft_365.enabled_api_connectors)[0]]
  cognito_identity_login_ids = local.provision_entraid_apps ? {
      for k, msft_connector in module.worklytics_connectors_msft_365.enabled_api_connectors :
      k => msft_connector.connector.client_id
    } : {
      "shared" : local.shared_connector.connector.client_id
    }
}

module "cognito_identity" {
  count = local.msft_365_enabled ? 1 : 0 # only provision identity pool if MSFT-365 connectors are enabled

  source = "../../modules/aws-cognito-identity-cli"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-cognito-identity-cli?ref=v0.4.57"

  aws_region       = data.aws_region.current.id
  aws_role         = var.aws_assume_role_arn
  identity_pool_id = module.cognito_identity_pool[0].pool_id
  login_ids = {
    for k, client_id in local.cognito_identity_login_ids :
     k => "${local.developer_provider_name}=${client_id}"
  }
}

resource "aws_iam_role_policy_attachment" "cognito_lambda_policy" {
  for_each = module.worklytics_connectors_msft_365.enabled_api_connectors

  role       = module.psoxy.api_connector_instances[each.key].instance_role_name
  policy_arn = module.cognito_identity_pool[0].policy_arn
}

locals {

  enabled_to_entraid_object = { for k, msft_connector in module.worklytics_connectors_msft_365.enabled_api_connectors : k => {
      connector_id: msft_connector.connector.id
      display_name: msft_connector.display_name
    }
  }
  shared_to_entraid_object = {
    "shared" : {
      connector_id: try(local.shared_connector.connector.id, null),
      display_name: "Shared"
    }
  }
}

module "msft_connection_auth_federation" {
  for_each = local.provision_entraid_apps ? local.enabled_to_entraid_object : local.shared_to_entraid_object

  source = "../../modules/azuread-federated-credentials"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-federated-credentials?ref=v0.4.57"

  application_object_id = each.value.connector_id
  display_name          = "${local.env_qualifier}AccessFromAWS"
  description           = "AWS federation to be used for ${local.env_qualifier} Connectors - ${each.value.display_name}${var.connector_display_name_suffix}"
  issuer                = "https://cognito-identity.amazonaws.com"
  audience              = module.cognito_identity_pool[0].pool_id
  subject               = module.cognito_identity[0].identity_id[each.key]
}

locals {
  msft_api_connectors_with_auth = {
    for k, msft_connector in module.worklytics_connectors_msft_365.enabled_api_connectors :
    k => merge(msft_connector, {
      environment_variables = merge(try(msft_connector.environment_variables, {}),
        {
          IDENTITY_POOL_ID  = module.cognito_identity_pool[0].pool_id,
          IDENTITY_ID       = try(module.cognito_identity[0].identity_id[k], module.cognito_identity[0].identity_id["shared"]),
          DEVELOPER_NAME_ID = local.developer_provider_name
        }
      )
    })
  }
}

output "msft_365_api_clients" {
  description = "Map of API client identifiers. Useful for configuration of clients, terraform migration."
  value       = module.worklytics_connectors_msft_365.api_clients
}
