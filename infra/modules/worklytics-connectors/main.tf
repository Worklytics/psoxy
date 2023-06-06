
module "worklytics_connector_specs" {
  source = "../../modules/worklytics-connector-specs"

  enabled_connectors = var.enabled_connectors
}

locals {
  long_access_parameters = {
    for entry in module.worklytics_connector_specs.enabled_oauth_secrets_to_create :
    "${entry.connector_name}.${entry.secret_name}" => entry
  }
  env_vars_for_locker = distinct(flatten([
    for k, v in module.worklytics_connector_specs.enabled_oauth_long_access_connectors : [
      for env_var in v.environment_variables : {
        connector_name = k
        env_var_name   = "OAUTH_REFRESH_TOKEN"
      } if try(v.environment_variables.USE_SHARED_TOKEN, null) != null
    ] if try(v.environment_variables, null) != null
  ]))

  env_vars_for_locker_parameters = { for entry in local.env_vars_for_locker : "${entry.connector_name}.${entry.env_var_name}" => entry }
  long_access_parameters_by_connector = { for k, spec in module.worklytics_connector_specs.enabled_oauth_long_access_connectors :
    k => [for secret in spec.secured_variables : "${k}.${secret.name}"]
  }
}

module "source_token_external_todo" {
  for_each = module.worklytics_connector_specs.enabled_oauth_long_access_connectors_todos

  source = "../../modules/source-token-external-todo"

  source_id                         = each.key
  connector_specific_external_steps = each.value.external_token_todo
  todo_step                         = var.todo_step
}

locals {
  # TODO: deal w/ adding the OAUTH_REFRESH_TOKEN_STUFF from above
  enabled_api_connectors  = module.worklytics_connector_specs.enabled_oauth_long_access_connectors
  enabled_bulk_connectors = module.worklytics_connector_specs.enabled_bulk_connectors
}
