output "enabled_api_connectors" {
  description = "List of enabled api connectors"
  value       = local.enabled_api_connectors
}

output "todos" {
  description = "List of TODOS for enabled api connectors"
  value       = local.todos
}

output "next_todo_step" {
  value = local.next_todo_step
}

output "api_clients" {
  description = "Map of API clients identifiers for Google Workspace connectors. Useful for migrations."
  value = { for k, v in module.google_workspace_connection :
    k => {
      service_account_id = v.service_account_id
      oauth_client_id    = v.service_account_numeric_id
    }
  }
}

output "provision_gcp_sa_keys" {
  description = "Whether Terraform should provision GCP service account keys for enabled connectors. False when provision_service_accounts or provision_keys is false in google_workspace_connector_settings."
  value       = local.provision_gcp_sa_keys
}

output "service_accounts_tf_managed_keys" {
  description = "Map of connector id to GCP service account id for connectors whose keys Terraform should create and manage."
  value       = local.service_accounts_tf_managed_keys
}

output "service_accounts_user_managed_keys" {
  description = "Map of connector id to GCP service account id for connectors whose keys must be created and stored outside Terraform."
  value       = local.service_accounts_user_managed_keys
}
