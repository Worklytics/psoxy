output "enabled_google_workspace_connectors" {
  description = "List of enabled Google Workspace connectors"
  value       = local.enabled_google_workspace_connectors
}

output "enabled_msft_365_connectors" {
  description = "List of enabled Microsoft 365 connectors"
  value       = local.enabled_msft_365_connectors
}

# TODO: better name for these; MSFT/Google are also OAuth; main difference is that those support
# configuring the oauth clients via Terraform (and their respective APIs), whereas these require
# data source admins to provision clients outside of Terraform
output "enabled_oauth_long_access_connectors" {
  description = "List of enabled OAuth connectors"
  value       = local.enabled_oauth_long_access_connectors
}

output "enabled_oauth_long_access_connectors_todos" {
  description = "List of TODOS for enabled OAuth connectors"
  value       = local.enabled_oauth_long_access_connectors_todos
}

output "enabled_oauth_secrets_to_create" {
  description = "List of secrets to create for enabled OAuth connectors"
  value       = local.enabled_oauth_secrets_to_create
}

output "enabled_lockable_oauth_secrets_to_create" {
  description = "List of secrets to create for enabled OAuth connectors that are lockable"
  value       = local.enabled_lockable_oauth_secrets_to_create
}

output "enabled_bulk_connectors" {
  description = "List of enabled bulk connectors"
  value       = local.enabled_bulk_connectors
}

# deprecated; remove in 0.5
output "available_connector_ids" {
  description = "List of available connector IDs (deprecated since 0.4.54)"
  value       = local.default_enabled_connector_ids
}

output "default_enabled_connector_ids" {
  description = "List of available connector IDs"
  value       = local.default_enabled_connector_ids
}

output "available_google_workspace_connectors" {
  description = "List of available Google Workspace connectors"
  value       = local.google_workspace_sources_backwards
}

output "available_msft_365_connectors" {
  description = "List of available Microsoft 365 connectors"
  value       = local.msft_365_connectors_backwards
}

output "available_oauth_data_source_connectors" {
  description = "List of available OAuth data source connectors (non-MSFT 365 / Google Workspace)"
  value       = local.oauth_long_access_connectors_backwards
}

output "available_bulk_connectors" {
  description = "List of available bulk connectors"
  value       = local.bulk_connectors
}
