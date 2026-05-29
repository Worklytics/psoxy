# Remote Resources (beta)

> **Status: beta** — This feature is functional but may evolve. Feedback welcome.

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
   for assets shared across all connectors (e.g., NLP models, LLM weights).

The resource service acts as a **failover** after local environment and config service lookups.
For example, if the `RULES` config property is not found in environment variables or the config/parameter store,
psoxy will check for a `rules.yaml` object at `{INSTANCE_RESOURCE_PATH}/rules.yaml` in the remote bucket.

A hardcoded local filesystem path (`/var/psoxy/resources`) is also checked before the remote
bucket, providing a fast-path for containerized or VM-based deployments where resources can be
mounted locally.

## Terraform Configuration

Host modules (`aws-host` and `gcp-host`) enable remote resource loading **per connector** via `enable_remote_resources` on each `api_connectors`, `bulk_connectors`, or `webhook_collectors` entry. This wires the **artifacts bucket** (used for deployment bundles) as the remote resource bucket for only those instances.

> [!IMPORTANT]
> - If you configure an existing bucket (e.g., by providing `artifacts_bucket_name`), the bucket must already exist.
> - The Terraform runner (the credentials running the `terraform` command) must have sufficient IAM permissions on that bucket to apply permissions (since it will grant read access to the proxy's service account or Lambda execution role).

### AWS (`aws-host`)

```hcl
module "psoxy" {
  source = "../../modules/aws-host"

  api_connectors = {
    "msft-copilot" = {
      source_kind            = "..."
      source_auth_strategy   = "..."
      target_host            = "..."
      enable_remote_resources = true  # OpenNLP, rules.yaml in bucket, etc.
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

  api_connectors = {
    "gcal" = {
      source_kind            = "..."
      source_auth_strategy   = "..."
      target_host            = "..."
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

### LLM model archives (genMetadata BETA)
Upload a **zip** of a Jlama SafeTensors model directory (must include `config.json`) for the **genMetadata** augment to `{SHARED_RESOURCE_PATH}/llm/` in the bucket. The archive name is derived from `PSOXY_GEN_MODEL` (default `tjake/Llama-3.2-1B-Instruct-JQ4`): slashes become `__`, with a `.zip` suffix — e.g. `llm/tjake__Llama-3.2-1B-Instruct-JQ4.zip`. Set per-connector `enable_gen_metadata = true` on `api_connectors` (which also enables remote resource loading for that connector). See [gen-metadata-augment.md](../development/gen-metadata-augment.md) for HuggingFace ids, cloud backends, and ops detail (cloud inference does not use `llm/` archives).

**Helper script** (download from HuggingFace, zip, upload):

```bash
# AWS — PREFIX is your SHARED_RESOURCE_PATH within the bucket (trailing slash optional)
./tools/fetch-gen-metadata-model.sh s3://REMOTE_RESOURCE_BUCKET/PREFIX/

# GCP — optional MODEL_ID overrides PSOXY_GEN_MODEL / default
./tools/fetch-gen-metadata-model.sh gs://REMOTE_RESOURCE_BUCKET/PREFIX/ tjake/Llama-3.2-1B-Instruct-JQ4

# Use an existing local SafeTensors directory instead of downloading
./tools/fetch-gen-metadata-model.sh --from-dir /path/to/model-dir s3://REMOTE_RESOURCE_BUCKET/PREFIX/
```

**Manual upload:**

```bash
cd /path/to/model-dir && zip -r ../tjake__Llama-3.2-1B-Instruct-JQ4.zip .
aws s3 cp ../tjake__Llama-3.2-1B-Instruct-JQ4.zip \
  s3://{REMOTE_RESOURCE_BUCKET}/{SHARED_RESOURCE_PATH}/llm/tjake__Llama-3.2-1B-Instruct-JQ4.zip

gsutil cp ../tjake__Llama-3.2-1B-Instruct-JQ4.zip \
  gs://{REMOTE_RESOURCE_BUCKET}/{SHARED_RESOURCE_PATH}/llm/tjake__Llama-3.2-1B-Instruct-JQ4.zip
```

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
