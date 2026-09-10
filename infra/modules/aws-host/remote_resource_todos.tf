# Markdown TODOs for uploading remote resource assets (OpenNLP).
# Rendered as outputs only; upload is performed outside Terraform via tools/*.sh scripts.

locals {
  # artifacts_bucket_name can be null in module tests / BYO-artifact setups
  remote_resource_s3_prefix = (
    module.psoxy.artifacts_bucket_name == null
    ? null
    : "s3://${module.psoxy.artifacts_bucket_name}/${local.shared_resource_path}"
  )

  opennlp_connector_ids = join(", ", [
    for k, v in merge(var.api_connectors, var.bulk_connectors, var.webhook_collectors) : k
    if try(v.enable_remote_resources, false)
  ])
}

output "remote_resource_opennlp_todo" {
  description = "TODO (markdown) for uploading OpenNLP models when any connector has enable_remote_resources."
  value = local.needs_opennlp_model_upload ? trimspace(<<-EOT
	## Upload OpenNLP models (sentenceMetadata augment)

	Connectors with `enable_remote_resources`: ${local.opennlp_connector_ids}

	OpenNLP binaries are not bundled in deployment JARs. From the **psoxy repository root**, download models and upload to the artifacts / remote-resources bucket in one step:

	```bash
	./tools/fetch-opennlp-models.sh ${local.remote_resource_s3_prefix}
	```

	That command uses this deployment's artifacts bucket and shared prefix (`${local.shared_resource_path}`). It requires `curl` and the AWS CLI (`aws`) with permission to write objects under that prefix.

	See [remote-resources.md](https://github.com/worklytics/psoxy/blob/main/docs/configuration/remote-resources.md).
	EOT
  ) : null
}

# Local/Jlama genMetadata (llm/*.zip) abandoned; cloud-only Bedrock.
# When Bedrock genMetadata is enabled, surface setup notes (model access is usually IAM-only for Nova).
output "remote_resource_gen_metadata_todo" {
  description = "TODO (markdown) for Bedrock genMetadata setup when any connector has enable_gen_metadata. Null when unused."
  value = local.gen_metadata_uses_bedrock ? trimspace(<<-EOT
	## Bedrock genMetadata setup

	Connectors with `enable_gen_metadata` use Amazon Bedrock. Default model: `us.amazon.nova-2-lite-v1:0` (US cross-region inference profile). Override with env `METADATA_GEN_MODEL` if needed (e.g. `eu.amazon.nova-2-lite-v1:0` or a Claude id).

	### Usually automatic (Terraform)

	- Lambda/connector IAM already allows `bedrock:InvokeModel` / `bedrock:Converse` on foundation models and inference profiles.
	- Prefer deploying the proxy in a **US** region when using the default `us.*` profile.

	### Manual / account steps (not fully automatable in Terraform)

	1. **Amazon Nova (default):** With Bedrock’s simplified model access, AWS first-party Nova models are generally available without a console “enable model” step. If invokes fail with access errors, confirm the account/region is not blocked by an SCP and that Bedrock is usable in that region ([model access](https://docs.aws.amazon.com/bedrock/latest/userguide/model-access.html)).
	2. **Anthropic Claude (only if you set `METADATA_GEN_MODEL` to a Claude id):** First-time Anthropic use in the account still requires a one-time use-case form (Bedrock console playground or `PutUseCaseForModelAccess`) — Terraform does not submit that form for you.
	3. Optional daily spend cap: set `gen_metadata_daily_cost_limit_usd` + `gen_metadata_budget_alert_emails` on aws-host (Budgets + IAM Deny). Provisioners need Budgets permissions — see `psoxy-constants` `required_aws_managed_policies_to_provision_gen_metadata`.

	See [gen-metadata-augment.md](https://github.com/worklytics/psoxy/blob/main/docs/development/alpha-features/gen-metadata-augment.md).
	EOT
  ) : null
}
