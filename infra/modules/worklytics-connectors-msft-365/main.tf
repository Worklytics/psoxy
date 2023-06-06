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

locals {
  msft_365_enabled = length(module.worklytics_connector_specs.enabled_msft_365_connectors) > 0
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

# TODO: coupled to AWS / identity pool; needs more refactoring!!!
# make this optional!?!? or two variants of module, with conditional??

module "cognito_identity_pool" {
  count = local.msft_365_enabled ? 1 : 0 # only provision identity pool if MSFT-365 connectors are enabled

  source = "../../modules/aws-cognito-pool"

  developer_provider_name = "azure-access"
  name                    = "azure-ad-federation"
}

module "cognito_identity" {
  count = local.msft_365_enabled ? 1 : 0 # only provision identity pool if MSFT-365 connectors are enabled

  source = "../../modules/aws-cognito-identity-cli"

  identity_pool_id = module.cognito_identity_pool[0].pool_id
  aws_region       = data.aws_region.current.id
  login_ids        = { for k in keys(module.msft_connection) : k => "${module.cognito_identity_pool[0].developer_provider_name}=${module.msft_connection[k].connector.application_id}" }
  aws_role         = var.aws_assume_role_arn # is this Terraform role, or the AWS PsoxyCaller role?
}

locals {
  enabled_rest_connectors = {
    for k, v in module.worklytics_connector_specs.enabled_msft_365_connectors :
    k => merge(v, {
      environment_variables = merge(v.environment_variables, {
        CLIENT_ID         = module.msft_connection[k].connector.application_id
        IDENTITY_POOL_ID  = module.cognito_identity_pool[0].pool_id,
        IDENTITY_ID       = module.cognito_identity[0].identity_id[k]
        DEVELOPER_NAME_ID = module.cognito_identity_pool[0].developer_provider_name
      })
    })
  }
}
