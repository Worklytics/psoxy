# NOTE: region used to be passed in as a variable; put it MUST match the region in which the lambda
# is provisioned, and that's implicit in the provider - so we should just infer from the provider
data "aws_region" "current" {}

locals {
  environment_id_prefix                 = "${var.environment_id}${length(var.environment_id) > 0 ? "-" : ""}"
  environment_id_display_name_qualifier = length(var.environment_id) > 0 ? " ${var.environment_id} " : ""
}


module "worklytics_connector_specs" {
  source = "../../modules/worklytics-connector-specs"

  enabled_connectors     = var.enabled_connectors
  msft_tenant_id         = var.msft_tenant_id
  example_msft_user_guid = var.example_msft_user_guid
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
  owners                            = toset(concat(data.azuread_users.owners.object_ids, [data.azuread_client_config.current.object_id]))
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
  todo_step                = var.todo_step
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
