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

locals {
  base_config_path = "${var.psoxy_base_dir}configs"
  bulk_sources = {
    "hris" = {
      source_kind = "hris"
    },
    #    "qualtrics" = {
    #      source_kind = "qualtrics"
    #    },
  }
}

module "worklytics_connector_specs" {
  source = "../../modules/worklytics-connector-specs"

  enabled_connectors = [
    "asana",
    "azure-ad",
    "outlook-cal",
    "outlook-mail",
  ]
}

module "psoxy-aws" {
  source = "../../modules/aws"

  aws_account_id                 = var.aws_account_id
  psoxy_base_dir                 = var.psoxy_base_dir
  caller_aws_arns                = var.caller_aws_arns
  caller_gcp_service_account_ids = var.caller_gcp_service_account_ids

  providers = {
    aws = aws
  }
}

data "azuread_client_config" "current" {}

locals {
  # this IS the correct ID for the user terraform is running as, which we assume is a user who's OK
  # to use the subject of examples. You can change it to any string you want.
  example_msft_user_guid = data.azuread_client_config.current.object_id

  # Microsoft 365 sources; add/remove as you wish
  # See https://docs.microsoft.com/en-us/graph/permissions-reference for all the permissions available in AAD Graph API
}

module "msft-connection" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  source = "../../modules/azuread-connection"

  display_name                      = "Psoxy Connector - ${each.value.display_name}${var.connector_display_name_suffix}"
  tenant_id                         = var.msft_tenant_id
  required_app_roles                = each.value.required_app_roles
  required_oauth2_permission_scopes = each.value.required_oauth2_permission_scopes
}

module "msft-connection-auth" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  source = "../../modules/azuread-local-cert"

  application_object_id = module.msft-connection[each.key].connector.id
  rotation_days         = 60
  cert_expiration_days  = 180
  certificate_subject   = var.certificate_subject
}

resource "aws_ssm_parameter" "client_id" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  name  = "PSOXY_${upper(replace(each.key, "-", "_"))}_CLIENT_ID"
  type  = "String"
  value = module.msft-connection[each.key].connector.application_id

  lifecycle {
    ignore_changes = [
      value
    ]
  }
}

resource "aws_ssm_parameter" "refresh_endpoint" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  name      = "PSOXY_${upper(replace(each.key, "-", "_"))}_REFRESH_ENDPOINT"
  type      = "String"
  overwrite = true
  value     = "https://login.microsoftonline.com/${var.msft_tenant_id}/oauth2/v2.0/token"

  lifecycle {
    ignore_changes = [
      value
    ]
  }
}


module "private-key-aws-parameters" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  source = "../../modules/private-key-aws-parameter"

  instance_id = each.key

  private_key_id = module.msft-connection-auth[each.key].private_key_id
  private_key    = module.msft-connection-auth[each.key].private_key
}

module "psoxy-msft-connector" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  source = "../../modules/aws-psoxy-rest"

  function_name         = "psoxy-${each.key}"
  source_kind           = each.value.source_kind
  path_to_config        = "${var.psoxy_base_dir}/configs/${each.value.source_kind}.yaml"
  path_to_function_zip  = module.psoxy-aws.path_to_deployment_jar
  function_zip_hash     = module.psoxy-aws.deployment_package_hash
  api_caller_role_arn   = module.psoxy-aws.api_caller_role_arn
  aws_assume_role_arn   = var.aws_assume_role_arn
  example_api_calls     = each.value.example_calls
  aws_account_id        = var.aws_account_id
  path_to_repo_root     = var.psoxy_base_dir
  environment_variables = {
    PSEUDONYMIZE_APP_IDS = tostring(var.pseudonymize_app_ids)
    IS_DEVELOPMENT_MODE  = "true"
  }

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
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  source = "../../modules/azuread-grant-all-users"

  application_id           = module.msft-connection[each.key].connector.application_id
  oauth2_permission_scopes = each.value.required_oauth2_permission_scopes
  app_roles                = each.value.required_app_roles
  application_name         = each.key
}


module "worklytics-psoxy-connection" {
  for_each = module.worklytics_connector_specs.enabled_msft_365_connectors

  source = "../../modules/worklytics-psoxy-connection-aws"

  psoxy_endpoint_url = module.psoxy-msft-connector[each.key].endpoint_url
  display_name       = "${each.value.display_name} via Psoxy${var.connector_display_name_suffix}"
  aws_region         = var.aws_region
  aws_role_arn       = module.psoxy-aws.api_caller_role_arn
}


# BEGIN LONG ACCESS AUTH CONNECTORS
locals {
  enabled_oauth_long_access_connectors_todos = { for k, v in module.worklytics_connector_specs.enabled_oauth_long_access_connectors : k => v if v.external_token_todo != null }
}

# Create secret (later filled by customer)
resource "aws_ssm_parameter" "long-access-token-secret" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  name        = "PSOXY_${upper(replace(each.key, "-", "_"))}_ACCESS_TOKEN"
  type        = "SecureString"
  description = "The long lived token for `psoxy-${each.key}`"
  value       = sensitive("TODO: fill me with a real token!! (via AWS console)")

  lifecycle {
    ignore_changes = [
      value # we expect this to be filled via Console, so don't want to overwrite it with the dummy value if changed
    ]
  }
}

module "aws-psoxy-long-auth-connectors" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  source = "../../modules/aws-psoxy-rest"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-rest?ref=v0.4.1"


  function_name                         = "psoxy-${each.key}"
  path_to_function_zip                  = module.psoxy-aws.path_to_deployment_jar
  function_zip_hash                     = module.psoxy-aws.deployment_package_hash
  path_to_config                        = "${local.base_config_path}/${each.value.source_kind}.yaml"
  aws_assume_role_arn                   = var.aws_assume_role_arn
  aws_account_id                        = var.aws_account_id
  api_caller_role_arn                   = module.psoxy-aws.api_caller_role_arn
  source_kind                           = each.value.source_kind
  path_to_repo_root                     = var.psoxy_base_dir
  example_api_calls                     = each.value.example_api_calls
  example_api_calls_user_to_impersonate = each.value.example_api_calls_user_to_impersonate
  environment_variables                 = {
    PSEUDONYMIZE_APP_IDS = tostring(var.pseudonymize_app_ids)
    IS_DEVELOPMENT_MODE  = "true"
  }

  parameters = [
    module.psoxy-aws.salt_secret,
    aws_ssm_parameter.long-access-token-secret[each.key]
  ]


}

module "source_token_external_todo" {
  for_each = local.enabled_oauth_long_access_connectors_todos

  source = "../../modules/source-token-external-todo"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/source-token-external-todo?ref=v0.4.1"

  source_id                         = each.key
  host_cloud                        = "aws"
  connector_specific_external_steps = each.value.external_token_todo
  token_secret_id                   = aws_ssm_parameter.long-access-token-secret[each.key].name
}

module "worklytics-psoxy-connection-oauth-long-access" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  source = "../../modules/worklytics-psoxy-connection-aws"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection-aws?ref=v0.4.1"

  psoxy_endpoint_url = module.aws-psoxy-long-auth-connectors[each.key].endpoint_url
  display_name       = "${each.value.display_name} via Psoxy${var.connector_display_name_suffix}"
  aws_region         = var.aws_region
  aws_role_arn       = module.psoxy-aws.api_caller_role_arn
}

# END LONG ACCESS AUTH CONNECTORS


module "psoxy-bulk" {
  for_each = local.bulk_sources

  source = "../../modules/aws-psoxy-bulk"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-bulk?ref=v0.4.1"

  aws_account_id       = var.aws_account_id
  aws_assume_role_arn  = var.aws_assume_role_arn
  instance_id          = each.key
  source_kind          = each.value.source_kind
  aws_region           = var.aws_region
  path_to_function_zip = module.psoxy-aws.path_to_deployment_jar
  function_zip_hash    = module.psoxy-aws.deployment_package_hash
  path_to_config       = "${var.psoxy_base_dir}configs/${each.value.source_kind}.yaml"
  api_caller_role_arn  = module.psoxy-aws.api_caller_role_arn
  api_caller_role_name = module.psoxy-aws.api_caller_role_name
  psoxy_base_dir       = var.psoxy_base_dir
}
