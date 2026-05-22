# genMetadata Augment (BETA)

> **Status:** BETA · PoC on branch `s225-gen-metadata-poc`
> **Since:** v0.6.x
> **Relates to:** [augments.md](augments.md), [sentence-metadata-augment.md](sentence-metadata-augment.md), [remote-resources.md](../configuration/remote-resources.md)

## Overview

The **genMetadata** augment generates structured JSON metadata alongside a source field using a pluggable generative backend. The augment name is implementation-agnostic (local GGUF today; Bedrock/Vertex planned).

Output appears as a sibling property: `+{sourceProperty}:genMetadata`.

## Rule configuration (required)

| Field | Required | Description |
|-------|----------|-------------|
| `jsonPaths` | yes | Source values to process |
| `prompt` | yes | Task instruction (included in rules SHA) |
| `outputSchema` | yes | JSON Schema predicate; invalid output is suppressed |

No `model`, `backend`, `quality`, or `maxTokens` in rules. Generation limits and model selection are deployment settings (see env vars below). The `outputSchema` gate ensures only structured, expected fields reach the response; it does not cap generation length — use `PSOXY_GEN_MAX_TOKENS` for that.

## Deployment configuration (env)

Read via `ConfigService` / `ProxyConfigProperty`:

| Variable | Default | Purpose |
|----------|---------|---------|
| `PSOXY_GEN_BACKEND` | `local` | `local` (BETA), `bedrock`, `vertex` (future) |
| `PSOXY_GEN_MODEL` | `llama-3.2-1b-instruct` | Logical model id → `{SHARED_RESOURCE_PATH}/llm/{id}.gguf` |
| `PSOXY_GEN_TIMEOUT_SECONDS` | `15` | Per-inference timeout |
| `PSOXY_GEN_MAX_INPUT_CHARS` | `4096` | Truncate source text before prompting |
| `PSOXY_GEN_MAX_TOKENS` | `256` | Max tokens to generate per inference |
| `ENABLE_GEN_METADATA` | unset | Set to `true` when Terraform `enable_gen_metadata` is used |

## Infrastructure

Use Terraform **`enable_gen_metadata`** (host module) or per-connector `enable_gen_metadata` on `api_connectors`:

- Sets `ENABLE_GEN_METADATA=true` on the function
- Floors memory at **4096 MB** unless the connector already sets a higher `memory_size_mb`
- Enables remote resource loading for GGUF weights (same as `enable_remote_resources`)

You do not need a separate runtime failure if memory is too low — operators should enable the flag when rules use `genMetadata`. Without the flag, augments still run but return `augment-gen-unavailable` if the model cannot load.

Manual setup (without the flag): `enable_remote_resources = true`, `memory_size_mb = 4096`, upload GGUF to `{SHARED_RESOURCE_PATH}/llm/`.

## Java / llama.cpp dependency

BETA uses [java-llama.cpp](https://github.com/kherud/java-llama.cpp) (`de.kherud:llama`), JNI bindings to [llama.cpp](https://github.com/ggml-org/llama.cpp). Meta and Google do not ship maintained Java inference SDKs for on-device GGUF; Apache does not provide a generative equivalent. Alternatives (ONNX Runtime GenAI, DJL) are heavier for Lambda-sized deployments. The dependency is scoped to `psoxy-core` only.

## MS Copilot PoC: prompt classification

Classifies `body.content` into one of 11 categories (`category` only). See commented example in `docs/sources/microsoft-365/msft-copilot/msft-copilot.yaml` and `MS_COPILOT_GEN_METADATA_AUGMENT` in `PrebuiltSanitizerRules.java`.

## Error handling

Non-fatal: failures throw `AugmentProcessingException` (caught in `AugmentProcessor`) and surface as `X-Psoxy-Warning` headers on the response.

| Code | Meaning |
|------|---------|
| `augment-gen-unavailable` | Model missing / not loaded |
| `augment-gen-inference-failed` | Inference or JSON parse failed |
| `augment-output-schema-mismatch` | Output failed `outputSchema` predicate |
| `augment-conflict-skipped` | Upstream `+` properties present |

## Open issues

- **Bedrock / Vertex backends** — same `GenMetadataBackend` SPI; env-driven for BETA.
- **Rule-level `backend` or `quality`** — deferred; env-only for BETA.
