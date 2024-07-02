terraform {
  required_version = ">= 1.3, < 1.8"
}

locals {
  environment_id_prefix                 = "${var.environment_id}${length(var.environment_id) > 0 ? "-" : ""}"
  environment_id_display_name_qualifier = length(var.environment_id) > 0 ? " ${var.environment_id} " : ""
}


module "worklytics_connector_specs" {
  source = "../../modules/worklytics-connector-specs"

  enabled_connectors                  = var.enabled_connectors
  msft_tenant_id                      = var.msft_tenant_id
  example_msft_user_guid              = var.example_msft_user_guid
  msft_teams_example_team_guid        = var.msft_teams_example_team_guid
  msft_teams_example_channel_guid     = var.msft_teams_example_channel_guid
  msft_teams_example_chat_guid        = var.msft_teams_example_chat_guid
  msft_teams_example_call_guid        = var.msft_teams_example_call_guid
  msft_teams_example_call_record_guid = var.msft_teams_example_call_record_guid
}

locals {
  todos_to_populate = { for k, v in module.worklytics_connector_specs.enabled_msft_365_connectors : k => v if try(v.external_token_todo != null, false) && var.todos_as_local_files }
  application_ids_for_teams_setup = join(",", compact([format("\"%s\"", module.msft_connection["msft-teams"].connector.application_id),
  try(format("\"%s\"", module.msft_connection["outlook-cal"].connector.application_id), null)]))
}

data "azuread_client_config" "current" {

}

data "azuread_users" "owners" {
  user_principal_names = var.msft_owners_email
}

module "msft_connection" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  source = "../../modules/azuread-connection"

  display_name                      = "Psoxy Connector - ${each.value.display_name}${local.environment_id_display_name_qualifier}"
  tenant_id                         = var.msft_tenant_id
  required_app_roles                = each.value.required_app_roles
  required_oauth2_permission_scopes = each.value.required_oauth2_permission_scopes
  owners = toset(concat(data.azuread_users.owners.object_ids, [
    data.azuread_client_config.current.object_id
  ]))
}

# grant required permissions to connectors via Azure AD
# (requires terraform configuration being applied by an Azure User with privileges to do this; it
#  usually requires a 'Global Administrator' for your tenant)
module "msft_365_grants" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  source = "../../modules/azuread-grant-all-users"

  psoxy_instance_id        = each.key
  application_id           = module.msft_connection[each.key].connector.application_id
  oauth2_permission_scopes = each.value.required_oauth2_permission_scopes
  app_roles                = each.value.required_app_roles
  application_name         = each.key
  todos_as_local_files     = var.todos_as_local_files
  todo_step                = var.todo_step
}

resource "local_file" "todo-with-external-todo" {
  for_each = local.todos_to_populate

  filename = module.msft_365_grants[each.key].filename
  content = format("%s\n## Setup\nThen, please follow next instructions for complete the setup: \n\n%s",
    module.msft_365_grants[each.key].todo,
    replace(each.value.external_token_todo, "%%entraid.application_ids%%",
  each.key == "msft-teams" ? local.application_ids_for_teams_setup : format("\"%s\"", module.msft_connection[each.key].connector.application_id)))
}

locals {
  enabled_api_connectors = {
    for k, v in module.worklytics_connector_specs.enabled_msft_365_connectors :
    k => merge(v, {
      connector = module.msft_connection[k].connector
      environment_variables = merge(v.environment_variables, {
        CLIENT_ID = module.msft_connection[k].connector.application_id
      })
    })
  }
}