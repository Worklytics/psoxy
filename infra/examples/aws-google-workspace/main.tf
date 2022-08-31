terraform {
  required_providers {
    # for the infra that will host Psoxy instances
    aws = {
      source  = "hashicorp/aws"
      version = "~> 4.12"
    }

    # for the API connections to Google Workspace
    google = {
      version = ">= 3.74, <= 5.0"
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


locals {
  base_config_path = "${var.psoxy_base_dir}/configs/"
}

module "worklytics_connector_specs" {
  source = "../../modules/worklytics-connector-specs"
  # source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-connector-specs?ref=v0.4.1"

  enabled_connectors = [
    "gdirectory",
    "gcal",
    "gmail",
    "gdrive",
    "google-chat",
    "google-meet",
    "asana",
    "slack-discovery-api",
    "zoom",
  ]
  google_workspace_example_user = var.google_workspace_example_user
}

module "psoxy-aws" {
  # source = "../../modules/aws" # to bind with local
  source = "git::https://github.com/worklytics/psoxy//infra/modules/aws?ref=v0.4.1"

  aws_account_id                 = var.aws_account_id
  psoxy_base_dir                 = var.psoxy_base_dir
  caller_aws_arns                = var.caller_aws_arns
  caller_gcp_service_account_ids = var.caller_gcp_service_account_ids
}

# holds SAs + keys needed to connect to Google Workspace APIs
resource "google_project" "psoxy-google-connectors" {
  name            = "Worklytics Connect%{if var.environment_name != ""} - ${var.environment_name}%{endif}"
  project_id      = var.gcp_project_id
  billing_account = var.gcp_billing_account_id
  folder_id       = var.gcp_folder_id # if project is at top-level of your GCP organization, rather than in a folder, comment this line out
  # org_id          = var.gcp_org_id # if project is in a GCP folder, this value is implicit and this line should be commented out

  # NOTE: these are provide because OFTEN customers have pre-existing GCP project; if such, there's
  # usually no need to specify folder_id/org_id/billing_account and have changes applied
  lifecycle {
    ignore_changes = [
      org_id,
      folder_id,
      billing_account,
    ]
  }
}


module "google-workspace-connection" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  # source = "../../modules/google-workspace-dwd-connection"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/google-workspace-dwd-connection?ref=v0.4.1"

  project_id                   = google_project.psoxy-google-connectors.project_id
  connector_service_account_id = "psoxy-${each.key}"
  display_name                 = "Psoxy Connector - ${each.value.display_name}${var.connector_display_name_suffix}"
  apis_consumed                = each.value.apis_consumed
  oauth_scopes_needed          = each.value.oauth_scopes_needed

  depends_on = [
    module.psoxy-aws,
    google_project.psoxy-google-connectors
  ]
}

module "google-workspace-connection-auth" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  # source = "../../modules/gcp-sa-auth-key-aws-secret"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-sa-auth-key-aws-secret?ref=v0.4.1"

  service_account_id = module.google-workspace-connection[each.key].service_account_id
  secret_id          = "PSOXY_${replace(upper(each.key), "-", "_")}_SERVICE_ACCOUNT_KEY"
}

module "psoxy-google-workspace-connector" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  # source = "../../modules/aws-psoxy-rest"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-rest?ref=v0.4.1"

  function_name                         = "psoxy-${each.key}"
  source_kind                           = each.key
  path_to_function_zip                  = module.psoxy-aws.path_to_deployment_jar
  function_zip_hash                     = module.psoxy-aws.deployment_package_hash
  path_to_config                        = "${local.base_config_path}/${each.key}.yaml"
  api_caller_role_arn                   = module.psoxy-aws.api_caller_role_arn
  aws_assume_role_arn                   = var.aws_assume_role_arn
  aws_account_id                        = var.aws_account_id
  path_to_repo_root                     = var.psoxy_base_dir
  example_api_calls                     = each.value.example_api_calls
  example_api_calls_user_to_impersonate = each.value.example_api_calls_user_to_impersonate

  parameters = [
    module.psoxy-aws.salt_secret,
    module.google-workspace-connection-auth[each.key].key_secret
  ]
}


module "worklytics-psoxy-connection-google-workspace" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  # source = "../../modules/worklytics-psoxy-connection-aws"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection-aws?ref=v0.4.1"

  psoxy_endpoint_url = module.psoxy-google-workspace-connector[each.key].endpoint_url
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

  # source = "../../modules/aws-psoxy-rest"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-rest?ref=v0.4.2"


  function_name        = "psoxy-${each.key}"
  path_to_function_zip = module.psoxy-aws.path_to_deployment_jar
  function_zip_hash    = module.psoxy-aws.deployment_package_hash
  path_to_config       = "${local.base_config_path}/${each.value.source_kind}.yaml"
  aws_assume_role_arn  = var.aws_assume_role_arn
  aws_account_id       = var.aws_account_id
  api_caller_role_arn  = module.psoxy-aws.api_caller_role_arn
  source_kind          = each.value.source_kind
  path_to_repo_root    = var.psoxy_base_dir
  example_api_calls    = each.value.example_api_calls

  parameters = [
    module.psoxy-aws.salt_secret,
    aws_ssm_parameter.long-access-token-secret[each.key]
  ]


}

module "source_token_external_todo" {
  for_each = local.enabled_oauth_long_access_connectors_todos

  #source = "../../modules/source-token-external-todo"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/source-token-external-todo?ref=v0.4.2"

  source_id                         = each.key
  host_cloud                        = "aws"
  connector_specific_external_steps = each.value.external_token_todo
  token_secret_id                   = aws_ssm_parameter.long-access-token-secret[each.key].name
}

module "worklytics-psoxy-connection" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors

  # source = "../../modules/worklytics-psoxy-connection-aws"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-psoxy-connection-aws?ref=v0.4.2"

  psoxy_endpoint_url = module.aws-psoxy-long-auth-connectors[each.key].endpoint_url
  display_name       = "${each.value.display_name} via Psoxy${var.connector_display_name_suffix}"
  aws_region         = var.aws_region
  aws_role_arn       = module.psoxy-aws.api_caller_role_arn
}

# END LONG ACCESS AUTH CONNECTORS


module "psoxy-hris" {
  # source = "../../modules/aws-psoxy-bulk"
  source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-bulk?ref=v0.4.2"

  aws_account_id       = var.aws_account_id
  aws_assume_role_arn  = var.aws_assume_role_arn
  instance_id          = "hris"
  source_kind          = "hris"
  aws_region           = var.aws_region
  path_to_function_zip = module.psoxy-aws.path_to_deployment_jar
  function_zip_hash    = module.psoxy-aws.deployment_package_hash
  path_to_config       = "${var.psoxy_base_dir}configs/hris.yaml"
  api_caller_role_arn  = module.psoxy-aws.api_caller_role_arn
  api_caller_role_name = module.psoxy-aws.api_caller_role_name
  psoxy_base_dir       = var.psoxy_base_dir
}
