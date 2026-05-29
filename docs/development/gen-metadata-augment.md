# genMetadata Augment (BETA)

> **Status:** BETA · PoC on branch `s225-gen-metadata-poc`
> **Since:** v0.6.x
> **Relates to:** [augments.md](augments.md), [sentence-metadata-augment.md](sentence-metadata-augment.md), [remote-resources.md](../configuration/remote-resources.md)

## Overview

The **genMetadata** augment generates structured JSON metadata alongside a source field using a pluggable generative backend. All backends share a [LangChain4j](https://github.com/langchain4j/langchain4j) `ChatModel` integration inside `psoxy-core`. **Local** inference uses embedded [Jlama](https://github.com/tjake/Jlama) (pure Java). **Bedrock** and **Vertex** cloud backends are planned.

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
| `PSOXY_GEN_BACKEND` | `local` | `local` (Jlama), `bedrock` and `vertex` (future) |
| `PSOXY_GEN_MODEL` | `tjake/Llama-3.2-1B-Instruct-JQ4` | Jlama HuggingFace id (`owner/name`) or logical id for a cached local archive |
| `PSOXY_GEN_TIMEOUT_SECONDS` | `15` | Per-inference and model-load timeout |
| `PSOXY_GEN_MAX_INPUT_CHARS` | `4096` | Truncate source text before prompting |
| `PSOXY_GEN_MAX_TOKENS` | `256` | Max tokens to generate per inference |
| `ENABLE_GEN_METADATA` | unset | Set to `true` when that connector has Terraform `enable_gen_metadata = true` |

When `enable_gen_metadata` is set on an API connector, Terraform **appends** Jlama JVM flags to any existing `JAVA_TOOL_OPTIONS` from `general_environment_variables` or that connector's `environment_variables` (`--add-modules=jdk.incubator.vector --enable-preview --enable-native-access=ALL-UNNAMED`).

## Infrastructure

Set **`enable_gen_metadata = true`** on individual `api_connectors` entries (API connectors only):

- Sets `ENABLE_GEN_METADATA=true` on that function
- Sets `JAVA_TOOL_OPTIONS` for Jlama on that function's JVM
- Floors memory at **4096 MB** on that connector unless a higher `memory_size_mb` / `available_memory_mb` is set
- Enables remote resource loading for that connector (for `llm/*.zip` model archives)

You do not need a separate runtime failure if memory is too low — enable the flag on connectors whose rules use `genMetadata`. Without the flag, augments still run but return `augment-gen-unavailable` if the model cannot load.

Manual setup (without Terraform flags): set env vars yourself, `memory_size_mb = 4096`, enable remote bucket access, and upload a model archive (see below).

## Java / LangChain4j

BETA uses **LangChain4j** with the **Jlama** provider for local embedded inference (`langchain4j` **1.15.0**, `langchain4j-jlama` **1.15.0-beta25**, `jlama-native` in `psoxy-core`). Maven Central does not publish a non-beta `langchain4j-jlama` artifact for the 1.x `ChatModel` API (older **0.36.2** uses a different API). The beta suffix is LangChain4j’s release channel for this integration module, not a separate fork. This avoids JNI bindings to llama.cpp that can take down the JVM on native faults. The same `ChatModel` abstraction will back **Bedrock** (`langchain4j-bedrock`, AWS bundle only) and **Vertex** (`langchain4j-vertex-ai-gemini`, GCP bundle only) when implemented.

Jlama loads models in **SafeTensors** layout (HuggingFace-style directory with `config.json`), not single-file GGUF.

### Local model deployment

1. **HuggingFace id (dev / first run):** set `PSOXY_GEN_MODEL` to a Jlama-compatible id such as `tjake/Llama-3.2-1B-Instruct-JQ4`. Jlama may download weights on first use (requires outbound network from the function).
2. **Production (recommended):** zip a SafeTensors model directory and upload to `{SHARED_RESOURCE_PATH}/llm/{cache-dir-name}.zip`, where `{cache-dir-name}` is `PSOXY_GEN_MODEL` with `/` replaced by `__` (e.g. `tjake__Llama-3.2-1B-Instruct-JQ4.zip` for `tjake/Llama-3.2-1B-Instruct-JQ4`). The runtime extracts the archive into a temp cache before loading.

**Example `PSOXY_GEN_MODEL` values** (Jlama / HuggingFace; prefer small instruct models on the 4096 MB floor):

| `PSOXY_GEN_MODEL` | Notes |
|-------------------|-------|
| `tjake/Llama-3.2-1B-Instruct-JQ4` | Default; pre-quantized Jlama build |
| `tjake/Llama-3.2-3B-Instruct-JQ4` | Higher quality; verify memory |
| `tjake/Qwen2.5-1.5B-Instruct-JQ4` | Strong small instruct |
| `tjake/Phi-3.5-mini-instruct-JQ4` | Compact |
| `tjake/gemma-2-2b-it-JQ4` | Google small instruct |

See [tjake on Hugging Face](https://huggingface.co/tjake) for other pre-quantized `-JQ4` builds. Logical ids without `/` (e.g. `llama-3.2-1b-instruct`) work when the matching `llm/llama-3.2-1b-instruct.zip` archive is present.

## MS Copilot PoC: prompt classification

Classifies `body.content` into one of 11 categories (`category` only). See commented example in `docs/sources/microsoft-365/msft-copilot/msft-copilot.yaml` and `MS_COPILOT_GEN_METADATA_AUGMENT` in `PrebuiltSanitizerRules.java`.

## Error handling

Non-fatal: failures throw `AugmentProcessingException` (caught in `AugmentProcessor`) and surface as `X-Psoxy-Warning` headers on the response.

| Code | Meaning |
|------|---------|
| `augment-gen-unavailable` | Model missing / not loaded / unsupported backend |
| `augment-gen-inference-failed` | Inference or JSON parse failed |
| `augment-output-schema-mismatch` | Output failed `outputSchema` predicate |
| `augment-conflict-skipped` | Upstream `+` properties present |

## Roadmap

### Cloud backends (`PSOXY_GEN_BACKEND=bedrock` | `vertex`) — planned

Implement via LangChain4j in `psoxy-core`, behind the existing `GenMetadataBackend` SPI:

| Backend | Module | Bundle | Credentials |
|---------|--------|--------|-------------|
| `bedrock` | `langchain4j-bedrock` | **AWS** Lambda / `psoxy-aws` only | Lambda execution role |
| `vertex` | `langchain4j-vertex-ai-gemini` | **GCP** Cloud Functions / `psoxy-gcp` only | Function service account |

`PSOXY_GEN_MODEL` becomes the cloud model id. Use native JSON schema (`ResponseFormat`) where supported; no `llm/` remote weights. Wrong backend on the wrong platform should fail clearly. IAM for Bedrock / Vertex AI will ship with per-connector `enable_gen_metadata` extensions.

### Alternative local engine: java-llama.cpp (`de.kherud`) — conditional

[java-llama.cpp](https://github.com/kherud/java-llama.cpp) (GGUF via JNI) is **not** in use. Revisit only if **both** are true:

1. Embedded local models prove valuable enough vs cloud inference, and
2. Jlama performance is a significant bottleneck for production workloads.

Rationale for deferring: JNI/native faults in llama.cpp can crash the JVM; LangChain4j + Jlama keeps inference in managed Java with a single `ChatModel` surface for local and cloud.

### Deferred

- **Rule-level `backend`, `model`, or `quality`** — env-only for BETA.
- **ONNX Runtime GenAI, DJL, Ollama sidecars** — out of scope for in-process serverless.
