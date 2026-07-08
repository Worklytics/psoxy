locals {
  provision_service_accounts = try(var.google_workspace_connector_settings["provision_service_accounts"], true)
  enable_apis                = try(var.google_workspace_connector_settings["enable_apis"], true)
  provision_gcp_sa_keys = (
    local.provision_service_accounts
    ? try(var.google_workspace_connector_settings["provision_keys"], var.provision_gcp_sa_keys)
    : false
  )
  gcp_sa_key_rotation_days = try(var.google_workspace_connector_settings["key_rotation_days"], var.gcp_sa_key_rotation_days)

  manual_steps_before_dwd = (local.enable_apis ? 0 : 1) + (local.provision_service_accounts ? 0 : 1)
  dwd_todo_step           = var.todo_step + local.manual_steps_before_dwd
  api_todo_step           = var.todo_step
  sa_todo_step            = var.todo_step + (local.enable_apis ? 0 : 1)
  key_todo_step           = local.dwd_todo_step + 1
}

terraform {
  required_version = ">= 1.3, < 2.0"
}

terraform {
  required_providers {
    # for the API connections to Google Workspace
    google = {
      version = "~> 5.0"
    }
  }
}

locals {
  environment_id_prefix                 = "${var.environment_id}${length(var.environment_id) > 0 ? "-" : ""}"
  environment_id_display_name_qualifier = length(var.environment_id) > 0 ? " ${var.environment_id} " : ""
}

module "worklytics_connector_specs" {
  source = "../../modules/worklytics-connector-specs"

  google_workspace_connector_settings = var.google_workspace_connector_settings

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
  provision_service_account    = local.provision_service_accounts
  enable_apis                  = local.enable_apis
  todos_as_local_files         = var.todos_as_local_files
  todo_step                    = local.dwd_todo_step
}

locals {

  api_enable_todos = {
    for id, connection in module.google_workspace_connection :
    id => templatefile("${path.module}/gcp-api-enable-todo.tftpl", {
      gcp_project_id : var.gcp_project_id
      connector_id : id
      apis_consumed : module.worklytics_connector_specs.enabled_google_workspace_connectors[id].apis_consumed
    })
  }

  sa_creation_todos = {
    for id, connection in module.google_workspace_connection :
    id => templatefile("${path.module}/gcp-sa-create-todo.tftpl", {
      gcp_project_id : var.gcp_project_id
      connector_id : id
      service_account_id : "${local.environment_id_prefix}${substr(id, 0, 30 - length(local.environment_id_prefix))}"
      display_name : "Psoxy Connector - ${local.environment_id_display_name_qualifier}${module.worklytics_connector_specs.enabled_google_workspace_connectors[id].display_name}"
      description : "Google API OAuth Client for ${module.worklytics_connector_specs.enabled_google_workspace_connectors[id].display_name}"
      expected_service_account_email : connection.service_account_email
    })
  }

  key_creation_todos = {
    for id, connection in module.google_workspace_connection :
    id => templatefile("${path.module}/gcp-sa-key-create-todo.tftpl", { gcp_project_id : var.gcp_project_id, gcp_service_account : connection.service_account_email, secret_prefix : connection.instance_id })
  }

  connector_todos = {
    for id, connection in module.google_workspace_connection :
    id => join("\n\n", [for part in [
      local.enable_apis ? null : local.api_enable_todos[id],
      local.provision_service_accounts ? null : local.sa_creation_todos[id],
      connection.todo,
      local.provision_gcp_sa_keys ? null : local.key_creation_todos[id],
    ] : part if part != null])
  }

  todos = [for id, connection in module.google_workspace_connection : local.connector_todos[id]]

  current_todo_step = try(max(values(module.google_workspace_connection)[*].next_todo_step...), local.dwd_todo_step)
  next_todo_step    = local.provision_gcp_sa_keys ? local.current_todo_step : local.current_todo_step + 1

  connectors_needing_manual_api_enablement = {
    for k, v in module.worklytics_connector_specs.enabled_google_workspace_connectors :
    k => v
    if !local.enable_apis
  }

  connectors_needing_manual_sa_creation = {
    for k, v in module.worklytics_connector_specs.enabled_google_workspace_connectors :
    k => v
    if !local.provision_service_accounts
  }

  service_accounts_tf_managed_keys = {
    for k, v in module.worklytics_connector_specs.enabled_google_workspace_connectors :
    k => module.google_workspace_connection[k].service_account_id
    if local.provision_gcp_sa_keys
  }

  service_accounts_user_managed_keys = {
    for k, v in module.worklytics_connector_specs.enabled_google_workspace_connectors :
    k => module.google_workspace_connection[k].service_account_id
    if !local.provision_gcp_sa_keys
  }
}

resource "local_file" "todo_gcp_api_enablement" {
  for_each = var.todos_as_local_files ? local.connectors_needing_manual_api_enablement : {}

  filename = "TODO ${local.api_todo_step} - Enable APIs for ${each.key}.md"
  content  = local.api_enable_todos[each.key]
}

resource "local_file" "todo_gcp_sa_creation" {
  for_each = var.todos_as_local_files ? local.connectors_needing_manual_sa_creation : {}

  filename = "TODO ${local.sa_todo_step} - Create Service Account for ${each.key}.md"
  content  = local.sa_creation_todos[each.key]
}

resource "local_file" "todo_gcp_sa_key_creation" {
  for_each = var.todos_as_local_files ? local.service_accounts_user_managed_keys : {}

  filename = "TODO ${local.key_todo_step} - Create Key for ${each.key}.md"
  content  = local.key_creation_todos[each.key]
}

module "google_workspace_connection_auth" {
  for_each = local.service_accounts_tf_managed_keys

  source = "../../modules/gcp-sa-auth-key"

  service_account_id     = each.value
  rotation_days          = local.gcp_sa_key_rotation_days
  tf_gcp_principal_email = var.tf_gcp_principal_email
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
            value               = try(module.google_workspace_connection_auth[k].key_value, "fill me")
            writable            = false
            sensitive           = true
            value_managed_by_tf = local.provision_gcp_sa_keys
            description         = "The API key for the GCP Service Account that is the OAuth Client for accessing the Google Workspace APIs used by the ${k} connector."
          }
        ]
      )
    })
  }
}
