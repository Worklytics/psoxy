
# DEPRECATED
output "required_aws_roles_to_provision_host" {
  value       = local.required_aws_roles_to_provision_host
  description = "DEPRECATED;The AWS roles required to provision infrastructure needed to host Psoxy in AWS"
}

output "required_aws_managed_policies_to_provision_host" {
  value       = local.required_aws_roles_to_provision_host
  description = "The AWS managed policies required to provision infrastructure needed to host Psoxy in AWS"
}

output "required_aws_managed_policies_to_consume_msft_365_source" {
  value = local.required_aws_managed_policies_to_consume_msft_365_source
  description = "The AWS managed policies required to provision infra needed to consume Microsoft 365 as a data source via Psoxy hosted in AWS"
}

output "required_gcp_roles_to_provision_host" {
  value       = local.required_gcp_roles_to_provision_host
  description = "The GCP roles required to provision infrastructure needed to host Psoxy in GCP"
}

output "required_gcp_roles_to_provision_google_workspace_source" {
  value       = local.required_gcp_roles_to_provision_google_workspace_source
  description = "The GCP roles required to provision OAuth Client(s) needed to use Google Workspace as a data source via Psoxy"
}

output "required_azuread_roles_to_provision_msft_365_source" {
  value       = local.required_azuread_roles_to_provision_msft_365_source
  description = "The Azure AD roles required to provision OAuth Client(s) needed to use Microsoft 365 as a data source via Psoxy"
}
