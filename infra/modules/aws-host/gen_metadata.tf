# Bedrock IAM is attached per connector in main.tf; this file holds the backend check and setup TODO.

check "gen_metadata_backend_aws_only" {
  assert {
    condition = alltrue([
      for k, backend in local.connector_gen_metadata_backend :
      backend == null || backend == "bedrock"
    ])
    error_message = "aws-host genMetadata backend must be \"bedrock\" (Vertex is GCP-only)."
  }
}

output "remote_resource_gen_metadata_todo" {
  description = "TODO (markdown) for Bedrock genMetadata setup when any connector has enable_gen_metadata. Null when unused."
  value = local.gen_metadata_uses_bedrock ? trimspace(<<-EOT
	## Bedrock genMetadata setup

	Connectors with `enable_gen_metadata` use Amazon Bedrock. Default model (Java): `us.amazon.nova-2-lite-v1:0` (US cross-region inference profile). Override with env `GEN_METADATA_MODEL` if needed (e.g. `eu.amazon.nova-2-lite-v1:0` or a Claude id).

	### Usually automatic (Terraform)

	- Lambda/connector IAM already allows `bedrock:InvokeModel` / `bedrock:Converse` on foundation models and inference profiles.
	- Prefer deploying the proxy in a **US** region when using the default `us.*` profile.

	### Manual / account steps (not fully automatable in Terraform)

	1. **Amazon Nova (default):** Use a CRIS inference profile id (`us.amazon.nova-2-lite-v1:0`), not the bare foundation-model id (`amazon.nova-2-lite-v1:0`) — on-demand invoke of the bare id returns 400. With Bedrock’s simplified model access, AWS first-party Nova is generally available without a console “enable model” step. If invokes fail with access errors, confirm the account/region is not blocked by an SCP and that Bedrock is usable in that region ([model access](https://docs.aws.amazon.com/bedrock/latest/userguide/model-access.html)).
	2. **Anthropic Claude (only if you set `GEN_METADATA_MODEL` to a Claude id):** First-time Anthropic use in the account still requires a one-time use-case form (Bedrock console playground or `PutUseCaseForModelAccess`) — Terraform does not submit that form for you.

	See [gen-metadata-augment.md](https://github.com/worklytics/psoxy/blob/main/docs/development/alpha-features/gen-metadata-augment.md).
	EOT
  ) : null
}
