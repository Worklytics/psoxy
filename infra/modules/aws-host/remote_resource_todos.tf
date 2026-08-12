# Markdown TODOs for uploading remote resource assets (OpenNLP).
# Rendered as outputs only; upload is performed outside Terraform via tools/*.sh scripts.

locals {
  remote_resource_s3_prefix = "s3://${module.psoxy.artifacts_bucket_name}/${local.shared_resource_path}"

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

# Local/Jlama genMetadata (llm/*.zip) abandoned; cloud-only Bedrock. Kept null for callers that still reference this output.
output "remote_resource_gen_metadata_todo" {
  description = "Deprecated: always null. Local/Jlama genMetadata model upload is no longer supported (use Bedrock)."
  value       = null
}
