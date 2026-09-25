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

output "webhook_test_caller_role_arn" {
  description = "ARN of the role granted invoke, KMS, and bucket access for webhook collector tests."
  value       = module.psoxy.webhook_test_caller_role_arn
}

output "test_aws_principal_arns" {
  description = "AWS principal ARNs allowed to test the deployment when provision_testing_infra is enabled."
  value       = local.test_aws_principal_arns
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

output "lookup_output_buckets" {
  description = "Output buckets for any lookup tables."
  value       = { for k, v in module.lookup_output : k => v.output_bucket }
}

output "pseudonym_salt" {
  description = "Value used to salt pseudonyms (SHA-256) hashes. If migrate to new deployment, you should copy this value."
  value       = module.psoxy.pseudonym_salt
  sensitive   = true
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

output "test_todos" {
  description = "List of todo steps to complete for testing, in markdown format."
  value       = values(module.api_connector)[*].todo
}

output "setup_todos" {
  description = "List of todo steps to complete for setup, in markdown format."
  value       = values(module.bulk_connector)[*].todo_setup
}

output "next_todo_step" {
  value = max(concat(
    values(module.api_connector)[*].next_todo_step,
    values(module.bulk_connector)[*].next_todo_step,
    [1]
  )...)
}

output "gen_metadata_todo" {
  description = "TODO steps to activate Bedrock for genMetadata, when any connector has enable_gen_metadata. Null when unused."
  value = local.gen_metadata_enabled ? trimspace(<<-EOT
	## Bedrock genMetadata setup

	1. Confirm Bedrock is usable in this AWS account and region. The default model id is `us.amazon.nova-2-lite-v1:0` (US cross-region inference profile). Use a CRIS id (`us.amazon.nova-2-lite-v1:0`), not the bare foundation-model id (`amazon.nova-2-lite-v1:0`).
	2. Prefer a **US** Lambda region when using that default `us.*` profile. To use another geography, set `GEN_METADATA_MODEL` (for example `eu.amazon.nova-2-lite-v1:0`).
	3. If invokes fail with access errors, check that an SCP is not blocking Bedrock in the account/region. See [Amazon Bedrock model access](https://docs.aws.amazon.com/bedrock/latest/userguide/model-access.html).

	Docs: [genMetadata](https://docs.worklytics.co/psoxy/development/alpha-features/gen-metadata-augment).
	EOT
  ) : null
}
