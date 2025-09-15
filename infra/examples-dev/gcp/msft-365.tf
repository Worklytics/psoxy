# BEGIN MSFT

module "worklytics_connectors_msft_365" {
  source = "../../modules/worklytics-connectors-msft-365"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-connectors-msft-365?ref=rc-v0.5.10"

  enabled_connectors                         = var.enabled_connectors
  environment_id                             = var.environment_name
  msft_tenant_id                             = var.msft_tenant_id
  example_msft_user_guid                     = var.example_msft_user_guid
  msft_owners_email                          = var.msft_owners_email
  msft_teams_example_team_guid               = var.msft_teams_example_team_guid
  msft_teams_example_channel_guid            = var.msft_teams_example_channel_guid
  msft_teams_example_chat_guid               = var.msft_teams_example_chat_guid
  msft_teams_example_call_guid               = var.msft_teams_example_call_guid
  msft_teams_example_call_record_guid        = var.msft_teams_example_call_record_guid
  msft_teams_example_online_meeting_join_url = var.msft_teams_example_online_meeting_join_url
  msft_connector_app_object_id               = var.msft_connector_app_object_id
  todos_as_local_files                       = var.todos_as_local_files
  todo_step                                  = 1
}

provider "azuread" {
  tenant_id = var.msft_tenant_id
}

locals {
  env_qualifier           = coalesce(var.environment_name, "psoxy")
  msft_365_enabled        = length(module.worklytics_connectors_msft_365.enabled_api_connectors) > 0
  developer_provider_name = "${local.env_qualifier}-azure-access"
}

module "msft-connection-auth-federation" {
  for_each = module.worklytics_connectors_msft_365.enabled_api_connectors

  source = "../../modules/azuread-federated-credentials"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-federated-credentials?ref=rc-v0.5.10"

  application_id = each.value.connector.id
  display_name   = "GcpFederation"
  description    = "Federation to be used for psoxy Connector - ${each.value.display_name}${local.env_qualifier}"
  issuer         = "https://accounts.google.com"
  subject        = module.psoxy.api_connector_gcp_execution_service_accounts[each.key].unique_id
}

locals {
  msft_api_connectors_with_auth = {
    for k, msft_connector in module.worklytics_connectors_msft_365.enabled_api_connectors :
    k => merge(msft_connector, {
      environment_variables = merge(try(msft_connector.environment_variables, {}),
        {
          # NOTE: hardcoded due a cycle (through api_connectors -> module.psoxy), ideally better if coming from
          # module.msft-connection-auth-federation[k].audience output variable
          # but for GCP is always "api://AzureADTokenExchange".
          AUDIENCE          = "api://AzureADTokenExchange"
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
