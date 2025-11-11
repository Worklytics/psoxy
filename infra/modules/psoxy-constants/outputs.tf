
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
  value       = local.required_aws_managed_policies_to_consume_msft_365_source
  description = "The AWS managed policies required to provision infra needed to consume Microsoft 365 as a data source via Psoxy hosted in AWS"
}

output "required_gcp_roles_to_provision_host" {
  value       = local.required_gcp_roles_to_provision_host
  description = "The GCP roles required to provision infrastructure needed to host Psoxy in GCP"
}

output "required_gcp_roles_to_use_vpc" {
  value       = local.required_gpc_roles_to_use_vpc
  description = "The GCP roles required to use a VPC to host Psoxy in GCP. Needed UNLESS you're merely using an existing VPC, subnetwork, and connector."
}

output "required_gcp_roles_to_provision_google_workspace_source" {
  value       = local.required_gcp_roles_to_provision_google_workspace_source
  description = "The GCP roles required to provision OAuth Client(s) needed to use Google Workspace as a data source via Psoxy"
}

output "required_gcp_permissions_to_host" {
  value       = local.min_gcp_permissions_to_host
  description = "The minimum GCP permissions required to host Psoxy in GCP"
}

output "required_gcp_apis_to_host" {
  value       = local.required_gcp_apis_to_host
  description = "The GCP Service APIs required to host Psoxy in GCP"
}

output "required_gcp_apis_to_provision_google_workspace_source" {
  value       = local.required_gcp_apis_to_provision_google_workspace_source
  description = "The GCP Service APIs required to provision OAuth Client(s) needed to use Google Workspace as a data source via Psoxy"
}

output "required_azuread_roles_to_provision_msft_365_source" {
  value       = local.required_azuread_roles_to_provision_msft_365_source
  description = "The Azure AD roles required to provision OAuth Client(s) needed to use Microsoft 365 as a data source via Psoxy"
}

output "aws_least_privileged_policy" {
  value       = local.aws_least_privileged_policy
  description = "ALPHA! YMMV. Least-privileged AWS policy to permit proxy provisioning/deployment. As of v0.4.55, use as basis for a policy; not yet tested for all deployment scenarios."
}
