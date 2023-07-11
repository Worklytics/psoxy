
module "worklytics_connector_specs" {
  source = "../../modules/worklytics-connector-specs"

  enabled_connectors = var.enabled_connectors

  github_organization       = var.github_organization
  example_github_repository = var.example_github_repository
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