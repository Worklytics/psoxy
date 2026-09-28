
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
  value       = local.required_gcp_roles_to_use_vpc
  description = "The GCP roles required to use a VPC to host Psoxy in GCP. Needed UNLESS you're merely using an existing VPC, subnetwork, and connector."
}

output "required_gcp_roles_to_use_external_api_alb" {
  value       = local.required_gcp_roles_to_use_external_api_alb
  description = "The GCP roles required when gcp-host provisions external_api_alb (global external ALB + optional Cloud Armor). Not needed for api_connector_external_lb_host (BYO ALB)."
}

output "required_gcp_permissions_to_use_external_api_alb" {
  value       = local.required_gcp_perms_to_use_external_api_alb
  description = "The GCP permissions required when gcp-host provisions external_api_alb. Subset of permissions in required_gcp_roles_to_use_external_api_alb, suitable for creating a custom IAM role."
}

output "required_gcp_roles_to_provision_google_workspace_source" {
  value       = local.required_gcp_roles_to_provision_google_workspace_source
  description = "The GCP roles always required to provision OAuth Client(s) for Google Workspace as a data source via Psoxy (Service Account Admin + Service Usage Admin). Applies to both service_account_key and workload_identity_federation. Add required_gcp_roles_to_provision_google_workspace_source_with_sa_keys or _with_wif as appropriate."
}

output "required_gcp_roles_to_provision_google_workspace_source_with_sa_keys" {
  value       = local.required_gcp_roles_to_provision_google_workspace_source_with_sa_keys
  description = "Additional GCP roles required when google_workspace_connector_settings.api_client_auth_method is service_account_key (the default): Service Account Key Admin. Not required for workload_identity_federation."
}

output "required_gcp_roles_to_provision_google_workspace_source_with_wif" {
  value       = local.required_gcp_roles_to_provision_google_workspace_source_with_wif
  description = "Additional GCP roles required when google_workspace_connector_settings.api_client_auth_method is workload_identity_federation on an AWS host: Workload Identity Pool Admin. Not required on GCP hosts (no WIF pool). Token Creator is granted to the proxy runtime, not the Terraform runner."
}

output "required_gcp_permissions_to_provision_google_workspace_source" {
  value       = local.required_gcp_perms_to_provision_google_workspace_source
  description = "ALPHA. Additional GCP permissions required to provision Google Workspace connectors with downloaded service-account keys (api_client_auth_method = service_account_key). These are in Service Account Key Admin, not in required_gcp_roles_to_provision_google_workspace_source. Not required for workload_identity_federation. Combine with required_gcp_permissions_to_provision_google_workspace_source_base for a custom role covering the default key path."
}

output "required_gcp_permissions_to_provision_google_workspace_source_base" {
  value       = local.required_gcp_perms_to_provision_google_workspace_source_base
  description = "ALPHA. GCP permissions always required to provision Google Workspace connectors (create DWD service accounts, bind IAM including Token Creator, enable APIs). Subset of required_gcp_roles_to_provision_google_workspace_source, suitable for a custom IAM role. Add the key or WIF permission lists as appropriate."
}

output "required_gcp_permissions_to_provision_google_workspace_source_with_wif" {
  value       = local.required_gcp_perms_to_provision_google_workspace_source_with_wif
  description = "ALPHA. Additional GCP permissions required to provision Google Workspace connectors with workload_identity_federation on an AWS host (WIF pool + provider). Subset of required_gcp_roles_to_provision_google_workspace_source_with_wif. Not required on GCP hosts."
}

output "required_gcp_apis_to_provision_google_workspace_source_with_wif" {
  value       = local.required_gcp_apis_to_provision_google_workspace_source_with_wif
  description = "Additional GCP Service APIs required when using workload_identity_federation for Google Workspace connectors. IAM Credentials is also in required_gcp_apis_to_provision_google_workspace_source; STS is needed for AWS WIF."
}

output "required_gcp_permissions_to_host" {
  value       = local.min_gcp_permissions_to_host
  description = "ALPHA. The minimum GCP permissions required to host Psoxy in GCP"
}

output "required_gcp_perms_to_provision_host" {
  value       = local.required_gcp_perms_to_provision_host
  description = "The GCP permissions required to provision infrastructure needed to host Psoxy in GCP. This is a subset of permissions contained in the roles defined in required_gcp_roles_to_provision_host, suitable for creating a custom IAM role."
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
  description = "The Microsoft Entra ID roles required to provision OAuth Client(s) needed to use Microsoft 365 as a data source via Psoxy"
}

output "aws_least_privileged_policy" {
  value       = local.aws_least_privileged_policy
  description = "ALPHA! YMMV. Least-privileged AWS policy to permit proxy provisioning/deployment. As of v0.4.55, use as basis for a policy; not yet tested for all deployment scenarios."
}
