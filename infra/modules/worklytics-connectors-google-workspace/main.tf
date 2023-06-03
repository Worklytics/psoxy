
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
  connector_service_account_id = "${local.environment_id_prefix}${substr(each.key, 0, 30 - length(local.environment_id_prefix))}"
  display_name                 = "Psoxy Connector - ${local.environment_id_display_name_qualifier}${each.value.display_name}"
  apis_consumed                = each.value.apis_consumed
  oauth_scopes_needed          = each.value.oauth_scopes_needed
  todo_step                    = var.todo_step
}

module "google_workspace_connection_auth" {
  for_each = module.worklytics_connector_specs.enabled_google_workspace_connectors

  source = "../../modules/gcp-sa-auth-key"

  service_account_id = module.google_workspace_connection[each.key].service_account_id
}


locals {
  enabled_rest_connectors = {
    for k, v in module.worklytics_connector_specs.enabled_google_workspace_connectors :
    k => merge(v, {
      # rather than this merge thing, should we this as a distinct output?
      # problem with that is that it's something of an implementation detail, right?
      secured_variables = concat(v.secured_variables, {
        name     = "SERVICE_ACCOUNT_KEY"
        value    = module.google_workspace_connection_auth[k].private_key
        writable = false
      })
    })
  }
}