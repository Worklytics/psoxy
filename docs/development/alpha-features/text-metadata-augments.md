# Text Metadata Augments (alpha)

> **Status: ALPHA**
> Augments that extract structural metadata from text fields. See [Augments](../augments.md) for the general augment mechanism.

## Available augments

| Augment | Documentation |
|---------|---------------|
| `sentenceMetadata` | [Sentence metadata augment](sentence-metadata-augment.md) — OpenNLP sentence structure, POS tags, and derived signals |

## Rule configuration

Text metadata augments are defined per-endpoint under `augments:`, parallel to `transforms`. Output appears as sibling properties named `+{sourceProperty}:sentenceMetadata`.

```yaml
endpoints:
  - pathTemplate: "/v1/example"
    augments:
      - !<sentenceMetadata>
        jsonPaths:
          - "$..prompt"
        taxonomy:
          CODE_ARTIFACT:
            - "code"
            - "function"
```

## OpenNLP model deployment

The `sentenceMetadata` augment requires OpenNLP model files (`en-sent.bin`, `en-pos-maxent.bin`, `en-chunker.bin`). These are **not** bundled in deployment JARs. Upload them to the [remote resources bucket](../../configuration/remote-resources.md) (requires `enable_remote_resources = true` on the host module).

Place them under `{SHARED_RESOURCE_PATH}/opennlp/` (e.g. `{SHARED_RESOURCE_PATH}/opennlp/en-sent.bin`). `{SHARED_RESOURCE_PATH}` defaults to `PATH_TO_SHARED_CONFIG` / your Terraform `config_parameter_prefix` (GCP) or shared secrets path (AWS).

### Helper script

Download locally, then upload to your artifacts / remote-resources bucket:

```bash
# AWS — PREFIX is your SHARED_RESOURCE_PATH within the bucket (trailing slash optional)
./tools/fetch-opennlp-models.sh s3://REMOTE_RESOURCE_BUCKET/PREFIX/

# GCP
./tools/fetch-opennlp-models.sh gs://REMOTE_RESOURCE_BUCKET/PREFIX/
```

With no argument, the script only downloads models into `java/gateway-core/src/main/resources/opennlp/` for local development and tests.

### Manual upload

```bash
aws s3 cp en-sent.bin s3://{REMOTE_RESOURCE_BUCKET}/{SHARED_RESOURCE_PATH}/opennlp/en-sent.bin
# ... repeat for en-pos-maxent.bin, en-chunker.bin
```

```bash
gsutil cp en-sent.bin gs://{REMOTE_RESOURCE_BUCKET}/{SHARED_RESOURCE_PATH}/opennlp/en-sent.bin
```
