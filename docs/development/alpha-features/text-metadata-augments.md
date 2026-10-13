# Text Metadata Augments (alpha)

> **Status: ALPHA**
> Augments that extract structural metadata from text fields. See [Augments](../augments.md) for the general augment mechanism.

## Available augments

| Augment | Documentation |
|---------|---------------|
| `textMetadata` | [Text metadata augment](text-metadata-augment.md) — OpenNLP sentence structure, POS tags, and derived signals |

## Rule configuration

Text metadata augments are defined per-endpoint under `augments:`, parallel to `transforms`. Output appears as sibling properties named `+{sourceProperty}:textMetadata`.

```yaml
endpoints:
  - pathTemplate: "/v1/example"
    augments:
      - !<textMetadata>
        jsonPaths:
          - "$..prompt"
        taxonomy:
          CODE_ARTIFACT:
            - "code"
            - "function"
```

## OpenNLP model deployment

The `textMetadata` augment requires OpenNLP model files (`en-sent.bin`, `en-pos-maxent.bin`, `en-chunker.bin`). These are **not** bundled in deployment JARs. Upload them to the [remote resources bucket](../../configuration/remote-resources.md) (requires `enable_remote_resources = true` on the host module).

Place them under the deployed function's `SHARED_RESOURCE_PATH` followed by `opennlp/`. Use the resource prefix, not the raw config/secrets prefix: for example, the GCP host converts `config_parameter_prefix = "psoxy_"` to `SHARED_RESOURCE_PATH = "psoxy/"`, so the model belongs at `psoxy/opennlp/en-sent.bin`. Host-generated resource prefixes already end in `/`. See [remote resource path prefixes](../../configuration/remote-resources.md#path-prefixes) for defaults and fallback rules.

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
