# Markdown TODOs for uploading remote resource assets (OpenNLP, genMetadata LLM archives).
# Rendered as outputs only; upload is performed outside Terraform via tools/*.sh scripts.

locals {
  remote_resource_gcs_prefix = "gs://${module.psoxy.artifacts_bucket_name}/${local.shared_resource_path}"
}

output "remote_resource_opennlp_todo" {
  description = "TODO (markdown) for uploading OpenNLP models when enable_remote_resources is true."
  value = var.enable_remote_resources ? trimspace(<<-EOT
	## Upload OpenNLP models (sentenceMetadata augment)

	OpenNLP binaries are not bundled in deployment JARs. If your rules use `sentenceMetadata` augments, upload the runtime models to your artifacts / remote-resources bucket.

	From the psoxy repository root:

	```bash
	./tools/fetch-opennlp-models.sh ${local.remote_resource_gcs_prefix}
	```

	See [remote-resources.md](https://github.com/worklytics/psoxy/blob/main/docs/configuration/remote-resources.md) for details.
	EOT
  ) : null
}

output "remote_resource_gen_metadata_todo" {
  description = "TODO (markdown) for uploading genMetadata LLM model archive when enable_gen_metadata is used."
  value = local.needs_gen_metadata_model_upload ? trimspace(<<-EOT
	## Upload genMetadata LLM model archive (BETA)

	Production deployments should load Jlama weights from the remote resources bucket instead of downloading from HuggingFace at runtime.

	Expected object: `${local.shared_resource_path}llm/${local.gen_metadata_archive_name}` (from `PSOXY_GEN_MODEL=${local.gen_metadata_model_id}`).

	From the psoxy repository root:

	```bash
	./tools/fetch-gen-metadata-model.sh ${local.remote_resource_gcs_prefix} ${local.gen_metadata_model_id}
	```

	Requires [HuggingFace CLI](https://huggingface.co/docs/huggingface_hub/en/guides/cli) (`pip install huggingface_hub`) for download. See [gen-metadata-augment.md](https://github.com/worklytics/psoxy/blob/main/docs/development/gen-metadata-augment.md).
	EOT
  ) : null
}
