# BEGIN MSFT

module "worklytics_connectors_msft_365" {
  source = "../../modules/worklytics-connectors-msft-365"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-connectors-msft-365?ref=rc-v0.4.33"


  enabled_connectors     = var.enabled_connectors
  environment_id         = var.environment_name
  msft_tenant_id         = var.msft_tenant_id
  example_msft_user_guid = var.example_msft_user_guid
  msft_owners_email      = var.msft_owners_email
  todo_step              = 1
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
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-cognito-pool?ref=rc-v0.4.33"

  developer_provider_name = local.developer_provider_name
  name                    = "${local.env_qualifier}-azure-ad-federation"
}

module "cognito_identity" {
  count = local.msft_365_enabled ? 1 : 0 # only provision identity pool if MSFT-365 connectors are enabled

  source = "../../modules/aws-cognito-identity-cli"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-cognito-identity-cli?ref=rc-v0.4.33"

  aws_region       = data.aws_region.current.id
  aws_role         = var.aws_assume_role_arn
  identity_pool_id = module.cognito_identity_pool[0].pool_id
  login_ids = {
    for k, v in module.worklytics_connectors_msft_365.enabled_api_connectors :
    k => "${local.developer_provider_name}=${v.connector.application_id}"
  }
}

resource "aws_iam_role_policy_attachment" "cognito_lambda_policy" {
  for_each = module.worklytics_connectors_msft_365.enabled_api_connectors

  role       = module.psoxy.api_connector_instances[each.key].instance_role_name
  policy_arn = module.cognito_identity_pool[0].policy_arn
}

module "msft_connection_auth_federation" {
  for_each = module.worklytics_connectors_msft_365.enabled_api_connectors

  source = "../../modules/azuread-federated-credentials"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/azuread-federated-credentials?ref=rc-v0.4.33"

  application_object_id = each.value.connector.id
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
          IDENTITY_ID       = module.cognito_identity[0].identity_id[k],
          DEVELOPER_NAME_ID = local.developer_provider_name
        }
      )
    })
  }
}
