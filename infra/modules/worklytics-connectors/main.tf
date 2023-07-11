
module "worklytics_connector_specs" {
  source = "../../modules/worklytics-connector-specs"

  enabled_connectors = var.enabled_connectors

  example_jira_issue_id = var.example_jira_issue_id
  example_msft_user_guid = var.example_msft_user_guid
  google_workspace_example_admin = var.google_workspace_example_admin
  google_workspace_example_user = var.google_workspace_example_user
  jira_cloud_id = var.jira_cloud_id
  jira_server_url = var.jira_server_url
  msft_tenant_id = var.msft_tenant_id
  salesforce_domain = var.salesforce_domain
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