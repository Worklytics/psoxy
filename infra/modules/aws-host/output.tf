/**
 * Outputs from aws-host module; as this host module is used directly in example, idea is to err on
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

output "caller_role_name" {
  value = module.psoxy.api_caller_role_name
}

output "caller_role_arn" {
  value = module.psoxy.api_caller_role_arn
}

output "api_connector_instances" {
  value = local.api_instances
}

output "bulk_connector_instances" {
  value = local.bulk_instances
}

output "lookup_output_buckets" {
  description = "Output buckets for any lookup tables."
  value       = { for k, v in module.lookup_output : k => v.output_bucket }
}

output "pseudonym_salt" {
  description = "Value used to salt pseudonyms (SHA-256) hashes. If migrate to new deployment, you should copy this value."
  value       = module.psoxy.pseudonym_salt
}

output "api_gateway_v2" {
  description = "the API Gateway V2 created, if any."
  value       = module.psoxy.api_gateway_v2
}

output "api_gateway_v2_stage" {
  description = "the API Gateway V2 stage created, if any."
  value       = module.psoxy.api_gateway_v2_stage
}

output "todos" {
  description = "List of todo steps to complete, in markdown format."
  value       = values(module.api_connector)[*].todo
}

output "next_todo_step" {
  value = max(concat(
    values(module.api_connector)[*].next_todo_step,
    values(module.bulk_connector)[*].next_todo_step,
    [1]
  )...)
}
