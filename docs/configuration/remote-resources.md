# Remote Resources

> [!NOTE]
> This feature is in beta. It is functional but may evolve; feedback welcome.

Psoxy supports loading resources (sanitization rules, NLP models, etc.) from a remote cloud storage
bucket (S3 on AWS, GCS on GCP). This enables configuration that is too large for environment
variables or parameter store entries to be managed centrally and loaded at runtime.

## How it Works

When the `REMOTE_RESOURCE_BUCKET` environment variable is set, psoxy will attempt to load resources
from the specified bucket using the function's execution role / service account. Resources are
resolved using a path prefix that mirrors the existing `PATH_TO_INSTANCE_CONFIG` hierarchy:

1. **Instance-specific resources** — loaded from `{INSTANCE_RESOURCE_PATH}/` within the bucket.
   The instance path defaults to `PATH_TO_INSTANCE_CONFIG`, so resources are co-located with
   instance configuration by default.
2. **Shared resources** — loaded from `{SHARED_RESOURCE_PATH}/` within the bucket. This path is
   for assets shared across all connectors (e.g., NLP models).

The resource service acts as a **failover** after local environment and config service lookups.
For example, if the `RULES` config property is not found in environment variables or the config/parameter store,
psoxy will check for a `rules.yaml` object at `{INSTANCE_RESOURCE_PATH}/rules.yaml` in the remote bucket.

A hardcoded local filesystem path (`/var/psoxy/resources`) is also checked before the remote
bucket, providing a fast-path for containerized or VM-based deployments where resources can be
mounted locally.

## Terraform Configuration

Remote resources are **opt-in**. You can enable them at the host level with `enable_remote_resources = true` on `aws-host` or `gcp-host`, and/or per connector with `enable_remote_resources` on individual `api_connectors`, `bulk_connectors`, or `webhook_collectors` entries. (genMetadata uses cloud backends — Bedrock on AWS, Vertex on GCP — and does not require remote bucket access for model archives.)

When enabled, the host module uses the artifacts bucket — either one you provide
(`artifacts_bucket_name` / `custom_artifacts_bucket_name`), one already provisioned for a local
deployment bundle, or a newly provisioned bucket when using a prebuilt `s3://` / `gs://`
`deployment_bundle`.

> [!IMPORTANT]
> If you supply an existing bucket (`artifacts_bucket_name` / `custom_artifacts_bucket_name`), it must already exist.
>
> The Terraform runner (the credentials running the `terraform` command) must have sufficient IAM permissions on that bucket to apply permissions, since it will grant read access to the proxy's service account or Lambda execution role.

### Connector specs and custom connectors

Prebuilt connectors in `worklytics-connector-specs` may set `enable_remote_resources` or `enable_gen_metadata` per connector (e.g. `msft-copilot` enables gen metadata). Those flags flow through to `aws-host` / `gcp-host` when the connector is enabled.

For ad-hoc connectors, set the flags on `custom_api_connectors` or `custom_bulk_connectors` in your root module (see `infra/examples-dev/*/variables.tf`).

### AWS (`aws-host`)

```hcl
module "psoxy" {
  source = "../../modules/aws-host"

  # ... existing configuration ...

  enable_remote_resources = true  # all connectors, or use per-connector flags below

  api_connectors = {
    "my-api" = {
      source_kind             = "..."
      source_auth_strategy    = "..."
      target_host             = "..."
      enable_remote_resources = true  # OpenNLP, rules.yaml in bucket, etc.
      enable_gen_metadata     = false
    }
  }
}
```

This will, for that connector only:
- Set `REMOTE_RESOURCE_BUCKET` on the Lambda to the artifacts bucket name
- Grant `s3:GetObject` permission on the configured path prefixes in the bucket to that Lambda's execution role

### GCP (`gcp-host`)

```hcl
module "psoxy" {
  source = "../../modules/gcp-host"

  # ... existing configuration ...

  enable_remote_resources = true  # all connectors, or use per-connector flags below

  api_connectors = {
    "gcal" = {
      source_kind             = "..."
      source_auth_strategy    = "..."
      target_host             = "..."
      enable_remote_resources = true
    }
  }
}
```

This will, for that connector only:
- Set `REMOTE_RESOURCE_BUCKET` on the Cloud Function to the artifacts bucket name
- Grant `roles/storage.objectViewer` on the bucket, scoped to the configured path prefixes using IAM conditions, to that function's service account

Remote resource paths use `/` as a hierarchy separator within the bucket (e.g. `psoxy-dev-erik/GCAL/rules.yaml` for shared prefix `psoxy-dev-erik/` and connector `gcal`). They are distinct from secret / parameter prefixes, which use a trailing `_` to separate names (e.g. `psoxy-dev-erik_GCAL_SOURCE`). When `INSTANCE_RESOURCE_PATH` / `SHARED_RESOURCE_PATH` are not set, psoxy falls back to the config paths and normalizes trailing `_` to `/` and strips any leading `/`.

## Environment Variables

| Variable                  | Description                                                                                        | Required |
|---------------------------|----------------------------------------------------------------------------------------------------|----------|
| `REMOTE_RESOURCE_BUCKET`  | Name of the S3/GCS bucket from which to load remote resources.                                     | Yes      |
| `INSTANCE_RESOURCE_PATH`  | Path prefix for instance-specific resources within the bucket. Defaults to `PATH_TO_INSTANCE_CONFIG`. | No       |
| `SHARED_RESOURCE_PATH`    | Path prefix for shared resources (NLP models, etc.) within the bucket. Defaults to `PATH_TO_SHARED_CONFIG`. | No       |

## IAM Permissions

The Terraform modules automatically grant minimal read permissions following the Principle of
Least Privilege. Access is limited to the configured resource path prefixes within the bucket:

- **AWS**: `s3:GetObject` only for objects under `{INSTANCE_RESOURCE_PATH}/` and
  `{SHARED_RESOURCE_PATH}/`
- **GCP**: object read access only for objects under `{INSTANCE_RESOURCE_PATH}/` and
  `{SHARED_RESOURCE_PATH}/`, enforced with IAM Conditions

No write, delete, or list permissions are granted.

## Use Cases

### Custom Rules
Upload a rules file to `{INSTANCE_RESOURCE_PATH}/rules.yaml` in the bucket. Psoxy will load it
if no `RULES` config property (env var, parameter store entry, etc.) is found.

### NLP Models (alpha)
OpenNLP model files (`en-sent.bin`, `en-pos-maxent.bin`, `en-chunker.bin`) are **not** bundled in
deployment JARs. If your connector rules use `sentenceMetadata` augments, set `enable_remote_resources = true` on that API connector and upload these models to the remote resources bucket.

Place them under `{SHARED_RESOURCE_PATH}/opennlp/` (e.g.
`{SHARED_RESOURCE_PATH}/opennlp/en-sent.bin`). `{SHARED_RESOURCE_PATH}` defaults to
`PATH_TO_SHARED_CONFIG` / your Terraform `config_parameter_prefix` (GCP) or shared secrets path
(AWS).

**Helper script** (download locally, then upload to your artifacts / remote-resources bucket):

```bash
# AWS — PREFIX is your SHARED_RESOURCE_PATH within the bucket (trailing slash optional)
./tools/fetch-opennlp-models.sh s3://REMOTE_RESOURCE_BUCKET/PREFIX/

# GCP
./tools/fetch-opennlp-models.sh gs://REMOTE_RESOURCE_BUCKET/PREFIX/
```

With no argument, the script only downloads models into
`java/gateway-core/src/main/resources/opennlp/` for local development and tests.

**Manual upload:**

```bash
aws s3 cp en-sent.bin s3://{REMOTE_RESOURCE_BUCKET}/{SHARED_RESOURCE_PATH}/opennlp/en-sent.bin
# ... repeat for en-pos-maxent.bin, en-chunker.bin
```

```bash
gsutil cp en-sent.bin gs://{REMOTE_RESOURCE_BUCKET}/{SHARED_RESOURCE_PATH}/opennlp/en-sent.bin
```

### LLM model archives (genMetadata) — abandoned

Local/Jlama genMetadata (zipped SafeTensors under `{SHARED_RESOURCE_PATH}/llm/`) is no longer supported. genMetadata is **cloud-only**: Bedrock on AWS, Vertex AI on GCP. Do not upload `llm/*.zip` archives; set `enable_gen_metadata = true` and use the host default backend (`bedrock` / `vertex`). See [gen-metadata-augment.md](../development/alpha-features/gen-metadata-augment.md).

## Uploading Resources

### AWS
```bash
aws s3 cp my-rules.yaml s3://{REMOTE_RESOURCE_BUCKET}/{INSTANCE_RESOURCE_PATH}/rules.yaml
```

### GCP
```bash
gcloud storage cp my-rules.yaml gs://{REMOTE_RESOURCE_BUCKET}/{INSTANCE_RESOURCE_PATH}/rules.yaml
```

## Troubleshooting

- **403 / Access Denied**: Ensure the Lambda execution role or Cloud Function service account has
  read access to the bucket. The Terraform modules handle this automatically, but custom
  deployments may need manual IAM grants.
- **Resource not loading**: Check CloudWatch / Cloud Logging for messages from `ResourceService`.
  Verify the object key matches `{PATH_PREFIX}/{RESOURCE_NAME}` exactly.
- **Local resources take precedence**: If a file exists at `/var/psoxy/resources/{name}`, it will
  be used instead of the remote bucket. This is by design for local development and testing.
