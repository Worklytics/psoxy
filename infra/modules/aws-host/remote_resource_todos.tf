# Markdown TODOs for uploading remote resource assets (OpenNLP, genMetadata LLM archives).
# Rendered as outputs only; upload is performed outside Terraform via tools/*.sh scripts.

locals {
  remote_resource_s3_prefix = "s3://${module.psoxy.artifacts_bucket_name}/${local.shared_resource_path}"

  opennlp_connector_ids = join(", ", [
    for k, v in merge(var.api_connectors, var.bulk_connectors, var.webhook_collectors) : k
    if try(v.enable_remote_resources, false)
  ])

  gen_metadata_connector_ids = join(", ", [
    for k, v in var.api_connectors : k if try(v.enable_gen_metadata, false)
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

output "remote_resource_gen_metadata_todo" {
  description = "TODO (markdown) for uploading genMetadata LLM model archive when any API connector has enable_gen_metadata."
  value = local.needs_gen_metadata_model_upload ? trimspace(<<-EOT
	## Upload genMetadata LLM model archive (BETA)

	API connectors with `enable_gen_metadata`: ${local.gen_metadata_connector_ids}

	Production deployments should load Jlama weights from the remote resources bucket instead of downloading from HuggingFace at runtime.

	Expected object key: `${local.shared_resource_path}llm/${local.gen_metadata_archive_name}` (from `PSOXY_GEN_MODEL=${local.gen_metadata_model_id}`).

	From the **psoxy repository root**, download the HuggingFace model, zip it, and upload to the artifacts bucket in one step:

	```bash
	./tools/fetch-gen-metadata-model.sh ${local.remote_resource_s3_prefix} ${local.gen_metadata_model_id}
	```

	That command uses:
	- **Destination:** `${local.remote_resource_s3_prefix}` (this deployment's artifacts bucket + shared resource prefix)
	- **Model:** `${local.gen_metadata_model_id}` (override with a second argument or `PSOXY_GEN_MODEL`)

	Requires `zip`, [HuggingFace CLI](https://huggingface.co/docs/huggingface_hub/en/guides/cli) (`pip install huggingface_hub`), and the AWS CLI (`aws`) with upload access. To use a local SafeTensors directory instead of downloading: add `--from-dir /path/to/model-dir` before the `s3://` URI.

	See [gen-metadata-augment.md](https://github.com/worklytics/psoxy/blob/main/docs/development/gen-metadata-augment.md).
	EOT
  ) : null
}
