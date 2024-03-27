terraform {
  required_version = ">= 1.3, < 1.8"
}

terraform {
  required_providers {
    # for the API connections to Google Workspace
    google = {
      version = ">= 3.74, <= 5.0"
    }
  }
}

locals {
  environment_id_prefix                 = "${var.environment_id}${length(var.environment_id) > 0 ? "-" : ""}"
  environment_id_display_name_qualifier = length(var.environment_id) > 0 ? " ${var.environment_id} " : ""
}

module "worklytics_connector_specs" {
  source = "../../modules/worklytics-connector-specs"

  enabled_connectors             = var.enabled_connectors
  google_workspace_example_admin = var.google_workspace_example_admin
  google_workspace_example_user  = var.google_workspace_example_user
}

module "google_workspace_connection" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/google-workspace-dwd-connection"

  project_id                   = var.gcp_project_id
  instance_id                  = each.key
  connector_service_account_id = "${local.environment_id_prefix}${substr(each.key, 0, 30 - length(local.environment_id_prefix))}"
  display_name                 = "Psoxy Connector - ${local.environment_id_display_name_qualifier}${each.value.display_name}"
  description                  = "Google API OAuth Client for ${each.value.display_name}"
  apis_consumed                = each.value.apis_consumed
  oauth_scopes_needed          = each.value.oauth_scopes_needed
  todos_as_local_files         = var.todos_as_local_files
  todo_step                    = var.todo_step
}

module "google_workspace_connection_auth" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/gcp-sa-auth-key"

  service_account_id = module.google_workspace_connection[each.key].service_account_id
}


locals {
  enabled_api_connectors = {
    for k, v in module.worklytics_connector_specs.enabled_google_workspace_connectors :
    k => merge(v, {
      # rather than this merge thing, should we this as a distinct output?
      # problem with that is that it's something of an implementation detail, right?
      secured_variables = concat(
        try([v.secured_variables], []),
        [
          {
            name                = "SERVICE_ACCOUNT_KEY"
            value               = module.google_workspace_connection_auth[k].key_value
            writable            = false
            sensitive           = true
            value_managed_by_tf = true
            description         = "The API key for the GCP Service Account that is the OAuth Client for accessing the Google Workspace APIs used by the ${k} connector."
          }
        ]
      )
    })
  }
}
