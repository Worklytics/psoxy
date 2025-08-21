terraform {
  required_version = ">= 1.3, < 2.0"
}

locals {
  environment_id_prefix                 = "${var.environment_id}${length(var.environment_id) > 0 ? "-" : ""}"
  environment_id_display_name_qualifier = length(var.environment_id) > 0 ? " ${var.environment_id} " : ""
}

# beginning of current month - used for example API calls to provide recent but fairly stable dates
# this will only change monthly, preventing churning while keeping examples relevant
resource "time_static" "month_start" {
  rfc3339 = formatdate("YYYY-MM-01'T'00:00:00Z", timestamp())
}

module "worklytics_connector_specs" {
  source = "../../modules/worklytics-connector-specs"

  enabled_connectors                         = var.enabled_connectors
  msft_tenant_id                             = var.msft_tenant_id
  example_msft_user_guid                     = var.example_msft_user_guid
  msft_teams_example_team_guid               = var.msft_teams_example_team_guid
  msft_teams_example_channel_guid            = var.msft_teams_example_channel_guid
  msft_teams_example_chat_guid               = var.msft_teams_example_chat_guid
  msft_teams_example_call_guid               = var.msft_teams_example_call_guid
  msft_teams_example_call_record_guid        = var.msft_teams_example_call_record_guid
  msft_teams_example_online_meeting_join_url = var.msft_teams_example_online_meeting_join_url
  example_api_calls_month_start              = time_static.month_start.id
}

locals {
  provision_entraid_apps  = var.msft_connector_app_object_id == null
  connectors_needing_apps = { for k, v in module.worklytics_connector_specs.enabled_msft_365_connectors : k => v if local.provision_entraid_apps }
}

data "azuread_client_config" "current" {
  count = local.provision_entraid_apps ? 1 : 0

}

data "azuread_users" "owners" {
  count = local.provision_entraid_apps ? 1 : 0

  user_principal_names = var.msft_owners_email
}


module "msft_connection" {
  for_each = local.connectors_needing_apps

  source = "../../modules/azuread-connection"

  display_name                      = "Psoxy Connector - ${each.value.display_name}${local.environment_id_display_name_qualifier}"
  tenant_id                         = var.msft_tenant_id
  required_app_roles                = each.value.required_app_roles
  required_oauth2_permission_scopes = each.value.required_oauth2_permission_scopes
  owners = toset(concat(data.azuread_users.owners[0].object_ids, [
    data.azuread_client_config.current[0].object_id
  ]))
}

# if an existing app object id is provided, use it as a shared app for ALL MSFT connectors
# (requires that it has the superset of permissions required by all connectors)
data "azuread_application" "existing_connector_app" {
  count = var.msft_connector_app_object_id == null ? 0 : 1

  object_id = var.msft_connector_app_object_id
}


# grant required permissions to connectors via Azure AD
# (requires terraform configuration being applied by an Azure User with privileges to do this; it
#  usually requires a 'Global Administrator' for your tenant)
module "msft_365_grants" {
  for_each = local.connectors_needing_apps

  source = "../../modules/azuread-grant-all-users"

  psoxy_instance_id        = each.key
  application_id           = module.msft_connection[each.key].connector.client_id
  oauth2_permission_scopes = each.value.required_oauth2_permission_scopes
  app_roles                = each.value.required_app_roles
  application_name         = each.key
  todos_as_local_files     = var.todos_as_local_files
  todo_step                = var.todo_step
}

module "msft_365_grant_to_shared" {
  count = local.provision_entraid_apps ? 0 : 1

  source = "../../modules/azuread-grant-all-users"

  psoxy_instance_id        = "msft-365"
  application_id           = data.azuread_application.existing_connector_app[0].client_id
  oauth2_permission_scopes = data.azuread_application.existing_connector_app[0].api[0].oauth2_permission_scopes[*].admin_consent_display_name
  # TODO: this is a list of GUIDs, so not very user-friendly
  app_roles            = flatten([for id in [for k, v in data.azuread_application.existing_connector_app[0].required_resource_access[*].resource_access[*] : v[*].id] : id[*]])
  application_name     = data.azuread_application.existing_connector_app[0].display_name
  todos_as_local_files = var.todos_as_local_files
  todo_step            = var.todo_step
}


# NOTE: this OVERWRITES the todo_file created by the azuread-grant-all-users module, if there's an
# external_token_todo to append to that file
locals {
  todos_to_populate = { for k, v in module.worklytics_connector_specs.enabled_msft_365_connectors :
    k => v if try(v.external_token_todo != null, false) && var.todos_as_local_files
  }
}

resource "local_file" "todo-with-external-todo" {
  for_each = local.todos_to_populate

  filename = module.msft_365_grants[each.key].filename
  content = <<EOT
${module.msft_365_grants[each.key].todo}
## Setup
Then, please follow next instructions to complete the setup:

${replace(each.value.external_token_todo, "%%entraid.client_id%%",
try(module.msft_connection[each.key].connector.client_id, data.azuread_application.existing_connector_app[0].client_id))}
EOT
}

locals {
  enabled_api_connectors = {
    for k, v in module.worklytics_connector_specs.enabled_msft_365_connectors :
    k => merge(v, {
      connector = try(module.msft_connection[k].connector, data.azuread_application.existing_connector_app[0])
      environment_variables = merge(v.environment_variables, {
        CLIENT_ID = try(module.msft_connection[k].connector.client_id, data.azuread_application.existing_connector_app[0].client_id)
      })
    })
  }
}
