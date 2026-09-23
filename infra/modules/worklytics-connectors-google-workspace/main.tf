locals {
  provision_service_accounts = try(var.google_workspace_connector_settings["provision_service_accounts"], true)
  enable_apis                = try(var.google_workspace_connector_settings["enable_apis"], true)
  api_client_auth_method     = try(var.google_workspace_connector_settings["api_client_auth_method"], "service_account_key")
  use_wif                    = local.api_client_auth_method == "workload_identity_federation"
  use_aws_wif                = local.use_wif && var.host_platform_id == "AWS"
  use_gcp_hosted_identity    = local.use_wif && var.host_platform_id == "GCP"
  provision_gcp_sa_keys = (
    local.use_wif
    ? false
    : (
      local.provision_service_accounts
      ? try(var.google_workspace_connector_settings["provision_keys"], var.provision_gcp_sa_keys)
      : false
    )
  )
  gcp_sa_key_rotation_days = try(var.google_workspace_connector_settings["key_rotation_days"], var.gcp_sa_key_rotation_days)

  manual_steps_before_dwd = (local.enable_apis ? 0 : 1) + (local.provision_service_accounts ? 0 : 1)
  dwd_todo_step           = var.todo_step + local.manual_steps_before_dwd
  api_todo_step           = var.todo_step
  sa_todo_step            = var.todo_step + (local.enable_apis ? 0 : 1)
  key_todo_step           = local.dwd_todo_step + 1

  # tflint-ignore: terraform_unused_declarations
  validate_aws_wif_account_id         = !local.use_aws_wif || (var.aws_account_id != null && var.aws_account_id != "")
  validate_aws_wif_account_id_message = "aws_account_id is required when google_workspace_connector_settings.api_client_auth_method is workload_identity_federation and host_platform_id is AWS."
  validate_aws_wif_account_id_check = regex(
    "^${local.validate_aws_wif_account_id_message}$",
    local.validate_aws_wif_account_id ? local.validate_aws_wif_account_id_message : ""
  )

  # tflint-ignore: terraform_unused_declarations
  validate_wif_host_platform         = !local.use_wif || contains(["AWS", "GCP"], var.host_platform_id)
  validate_wif_host_platform_message = "host_platform_id must be AWS or GCP when google_workspace_connector_settings.api_client_auth_method is workload_identity_federation."
  validate_wif_host_platform_check = regex(
    "^${local.validate_wif_host_platform_message}$",
    local.validate_wif_host_platform ? local.validate_wif_host_platform_message : ""
  )
}
terraform {
  required_version = "~> 1.7"

  required_providers {
    # for the API connections to Google Workspace
    google = {
      source  = "hashicorp/google"
      version = ">= 7.0"
    }
    local = {
      source  = "hashicorp/local"
      version = ">= 2.0"
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

  base_dir                       = var.base_dir
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
  api_client_auth_method       = local.api_client_auth_method
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
      (local.provision_gcp_sa_keys || local.use_wif) ? null : local.key_creation_todos[id],
    ] : part if part != null])
  }

  todos = [for id, connection in module.google_workspace_connection : local.connector_todos[id]]

  # Same value as max(connection.next_todo_step) (each connection is todo_step+1) without iterating
  # the DWD module — that waits on module close (local_file todos) and cycles through psoxy.todo_step
  # when SA keys are destroyed on a WIF cutover.
  next_todo_step = (local.provision_gcp_sa_keys || local.use_wif) ? local.dwd_todo_step + 1 : local.dwd_todo_step + 2

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

  service_accounts_tf_managed_keys = local.provision_gcp_sa_keys ? {
    for k, v in module.worklytics_connector_specs.enabled_google_workspace_connectors :
    k => module.google_workspace_connection[k].service_account_id
  } : {}

  service_accounts_user_managed_keys = {
    for k, v in module.worklytics_connector_specs.enabled_google_workspace_connectors :
    k => module.google_workspace_connection[k].service_account_id
    if !local.provision_gcp_sa_keys && !local.use_wif
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
  wif_process_identity_env = local.use_aws_wif ? {
    PROCESS_IDENTITY_SOURCE                   = "aws_wif"
    GCP_WIF_AUDIENCE                          = local.gcp_wif_audience
    GCP_WIF_SERVICE_ACCOUNT_IMPERSONATION_URL = local.gcp_wif_service_account_impersonation_url
    } : local.use_gcp_hosted_identity ? {
    PROCESS_IDENTITY_SOURCE = "gcp_hosted"
  } : {}

  # Deterministic DWD SA email (same formula as google-workspace-dwd-connection). Used for WIF env
  # so enabled_api_connectors does not wait on that module's close / local_file todos.
  gws_dwd_sa_id_raw = {
    for k, v in module.worklytics_connector_specs.enabled_google_workspace_connectors :
    k => lower(replace(trim("${local.environment_id_prefix}${substr(k, 0, 30 - length(local.environment_id_prefix))}", " "), " ", "-"))
  }
  gws_dwd_sa_email = {
    for k, raw in local.gws_dwd_sa_id_raw :
    k => format(
      "%s@%s.iam.gserviceaccount.com",
      length(raw) < 6 ? "psoxy-${raw}" : (length(raw) < 31 ? raw : substr(md5(raw), 0, 30)),
      var.gcp_project_id
    )
  }

  enabled_api_connectors = {
    for k, v in module.worklytics_connector_specs.enabled_google_workspace_connectors :
    k => merge(v, {
      environment_variables = merge(
        try(v.environment_variables, {}),
        local.use_wif ? merge({
          SERVICE_ACCOUNT_EMAIL = local.gws_dwd_sa_email[k]
        }, local.wif_process_identity_env) : {}
      )
      # rather than this merge thing, should we this as a distinct output?
      # problem with that is that it's something of an implementation detail, right?
      secured_variables = concat(
        try([v.secured_variables], []),
        local.use_wif ? [] : [
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
