/**
 * Outputs from gcp-host module; as this host module is used directly in example, idea is to err on
 * the side of more outputs here for extensibility, while keeping the example root configuration
 * fairly minimal.
 *
 * This enables exposure of outputs at top-level a matter of just adding them there, rather than
 * having to add them to the module, require customers to upgrade the module, and then expose them
 * in the root configuration example.
 */


output "path_to_deployment_jar" {
  description = "Path to the package to deploy (JAR)."
  value       = module.psoxy.path_to_deployment_jar
}

output "artifacts_bucket_id" {
  description = "The ID of the artifacts google_storage_bucket resource"
  value       = module.psoxy.artifacts_bucket_id
}

output "api_connector_instances" {
  value = local.api_instances
}

output "bulk_connector_instances" {
  value = local.bulk_instances
}

output "webhook_collector_instances" {
  value = local.webhook_collector_instances
}


output "pseudonym_salt" {
  description = "Value used to salt pseudonyms (SHA-256) hashes. If migrate to new deployment, you should copy this value."
  value       = module.psoxy.pseudonym_salt
  sensitive   = true
}

output "api_connector_gcp_execution_service_accounts" {
  description = "Service accounts used on Cloud Functions for API Connectors"
  value       = google_service_account.api_connectors
}

output "lookup_output_buckets" {
  description = "Output buckets for any lookup tables."
  value       = { for k, v in module.lookup_output : k => v.bucket_name }
}

output "todos" {
  description = "List of todo steps to complete, in markdown format."
  value       = values(module.api_connector)[*].todo
}

output "setup_todos" {
  description = "List of todo steps to complete for setup, in markdown format."
  value       = values(module.bulk_connector)[*].todo_setup
}

output "builder_sa_id" {
  value = module.psoxy.builder_sa_id
}

output "next_todo_step" {
  value = max(concat(
    values(module.api_connector)[*].next_todo_step,
    values(module.bulk_connector)[*].next_todo_step,
    [var.todo_step]
  )...)
}

output "external_alb_ip_address" {
  description = "Reserved global IP for the gcp-host-provisioned external ALB (null if external_api_alb is unset)."
  value       = try(google_compute_global_address.api_connector_alb[0].address, null)
}

output "external_alb_dns_setup" {
  description = "DNS instructions when external_api_alb.domain is set (managed TLS)."
  value       = try(module.external_api_alb[0].dns_setup, null)
}

output "external_alb_self_signed_ca_cert" {
  description = "Self-signed server certificate PEM when external_api_alb is enabled without a domain."
  value       = try(module.external_api_alb[0].self_signed_ca_cert, null)
  sensitive   = true
}

output "api_connector_external_lb_host" {
  description = "Effective external LB host for API connectors (provisioned ALB host/IP, BYO host, or null)."
  value       = local.api_connector_external_lb_host
}
