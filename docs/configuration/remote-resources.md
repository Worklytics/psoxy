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

By default, the host modules in this repository (`aws-host` and `gcp-host`) will configure the
`REMOTE_RESOURCE_BUCKET` for you if you set the `enable_remote_resources` variable to `true`. This
automatically wires the **artifacts bucket** (used for deployment bundles) as the remote resource bucket.

> [!IMPORTANT]
> - If you configure an existing bucket (e.g., by providing `artifacts_bucket_name`), the bucket must already exist.
> - The Terraform runner (the credentials running the `terraform` command) must have sufficient IAM permissions on that bucket to apply permissions (since it will grant read access to the proxy's service account or Lambda execution role).

### AWS (`aws-host`)

```hcl
module "psoxy" {
  source = "../../modules/aws-host"

  # ... existing configuration ...

  # Enable remote resource loading from the artifacts S3 bucket
  enable_remote_resources = true
}
```

This will:
- Set `REMOTE_RESOURCE_BUCKET` on every Lambda function to the artifacts bucket name
- Grant `s3:GetObject` permission on the configured path prefixes in the bucket to each Lambda's execution role

### GCP (`gcp-host`)

```hcl
module "psoxy" {
  source = "../../modules/gcp-host"

  # ... existing configuration ...

  # Enable remote resource loading from the artifacts GCS bucket
  enable_remote_resources = true
}
```

This will:
- Set `REMOTE_RESOURCE_BUCKET` on every Cloud Function to the artifacts bucket name
- Grant `roles/storage.objectViewer` on the bucket, scoped to the configured path prefixes using IAM conditions, to each function's service account

## Environment Variables

| Variable                  | Description                                                                                        | Required |
|---------------------------|----------------------------------------------------------------------------------------------------|----------|
| `REMOTE_RESOURCE_BUCKET`  | Name of the S3/GCS bucket containing remote resources.                                             | No       |
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
Upload OpenNLP model files (e.g., `en-sent.bin`) to `{SHARED_RESOURCE_PATH}/` in the bucket.
Psoxy augments can lazy-load these at runtime without inflating the deployment package.

### LLM Weights (future)
Smaller language models that fit in memory can be placed in the shared resource path for
on-the-fly inference within the proxy.

## Uploading Resources

### AWS
```bash
aws s3 cp my-rules.yaml s3://{REMOTE_RESOURCE_BUCKET}/{INSTANCE_RESOURCE_PATH}/rules.yaml
```

### GCP
```bash
gsutil cp my-rules.yaml gs://{REMOTE_RESOURCE_BUCKET}/{INSTANCE_RESOURCE_PATH}/rules.yaml
```

## Troubleshooting

- **403 / Access Denied**: Ensure the Lambda execution role or Cloud Function service account has
  read access to the bucket. The Terraform modules handle this automatically, but custom
  deployments may need manual IAM grants.
- **Resource not loading**: Check CloudWatch / Cloud Logging for messages from `ResourceService`.
  Verify the object key matches `{PATH_PREFIX}/{RESOURCE_NAME}` exactly.
- **Local resources take precedence**: If a file exists at `/var/psoxy/resources/{name}`, it will
  be used instead of the remote bucket. This is by design for local development and testing.
