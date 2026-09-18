# Remote Resources

> [!NOTE]
> This feature is in beta. It is functional but may evolve; feedback welcome.

Psoxy can load sanitization rules and NLP models from a remote bucket (S3 on AWS, GCS on GCP) when they are too large for environment variables or parameter store.

Set `enable_remote_resources = true` on the host module (`aws-host` or `gcp-host`). That sets `REMOTE_RESOURCE_BUCKET` on each function and grants read access to the artifacts bucket.

If you supply an existing bucket (`artifacts_bucket_name` / `custom_artifacts_bucket_name`), it must already exist, and the Terraform runner must be able to grant the proxy read access.

## Path prefixes

Instance and shared prefixes are **siblings** (not nested folders), so an IAM grant of `{SHARED_RESOURCE_PATH}*` does not cover instance objects:

| Prefix | Host default | Example |
|--------|--------------|---------|
| `INSTANCE_RESOURCE_PATH` | `{env}-{INSTANCE}/` | `psoxy-dev-erik-GCAL/` |
| `SHARED_RESOURCE_PATH` | `{env}/` | `psoxy-dev-erik/` |

If those env vars are unset, psoxy falls back to `PATH_TO_INSTANCE_CONFIG` / `PATH_TO_SHARED_CONFIG`, converting a trailing `_` to `/` and stripping a leading `/`.

Rules are loaded from `{INSTANCE_RESOURCE_PATH}/rules.yaml` when `RULES` is not set in env or config store. A local file at `/var/psoxy/resources/{name}` is checked first.

## Environment variables

| Variable | Description | Required |
|----------|-------------|----------|
| `REMOTE_RESOURCE_BUCKET` | S3/GCS bucket name | No |
| `INSTANCE_RESOURCE_PATH` | Instance prefix (defaults to `PATH_TO_INSTANCE_CONFIG`) | No |
| `SHARED_RESOURCE_PATH` | Shared prefix (defaults to `PATH_TO_SHARED_CONFIG`) | No |

## Use cases

### Custom rules

```bash
aws s3 cp my-rules.yaml s3://{REMOTE_RESOURCE_BUCKET}/{INSTANCE_RESOURCE_PATH}/rules.yaml
gcloud storage cp my-rules.yaml gs://{REMOTE_RESOURCE_BUCKET}/{INSTANCE_RESOURCE_PATH}/rules.yaml
```

### NLP models for `sentenceMetadata`

OpenNLP binaries (`en-sent.bin`, `en-pos-maxent.bin`, `en-chunker.bin`) are not in the deployment JAR. With `enable_remote_resources = true`, put them at `{SHARED_RESOURCE_PATH}/opennlp/{model}.bin`.

```bash
./tools/fetch-opennlp-models.sh s3://REMOTE_RESOURCE_BUCKET/PREFIX/
./tools/fetch-opennlp-models.sh gs://REMOTE_RESOURCE_BUCKET/PREFIX/
```

With no argument, the script only downloads models into `java/gateway-core/src/main/resources/opennlp/` for local tests.

### genMetadata

`!<genMetadata>` augments use Vertex AI or Bedrock, not this bucket. See [genMetadata](../development/alpha-features/gen-metadata-augment.md).

## Troubleshooting

- **403 / Access Denied**: the Lambda role or Cloud Function service account needs object read on the prefixes above. Terraform grants this when `enable_remote_resources` is true. S3 may return 403 (citing `s3:ListBucket`) instead of 404 when list is denied; psoxy treats that as missing and continues lookup.
- **Resource not loading**: check logs from `ResourceService`; the object key must match `{PATH_PREFIX}/{RESOURCE_NAME}`.
- **Local file wins**: `/var/psoxy/resources/{name}` is used instead of the bucket when present.
