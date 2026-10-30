# genMetadata Augment (BETA)

> **Status:** BETA · Alpha feature
> **Since:** v0.7.x
> **Relates to:** [augments.md](../augments.md), [remote-resources.md](../../configuration/remote-resources.md)

## Overview

**genMetadata** and **classify** call a cloud LLM to derive metadata from a matched source value and attach it as:

- **Scalar / leaf match** (e.g. `$.title`, `$..body.content`): sibling `+{sourceProperty}:{function}` on the parent object.
- **Object match** (e.g. `$` or `$[*]` when each match is a JSON object): `+self:{function}` on that same object; the whole matched Map is the LLM corpus.

This is an MVP for customers to try with **custom rules**. Prebuilt source rules do not enable these augments.

| Decision | Choice |
|----------|--------|
| Runtime | **`remote-llm` (default):** Amazon Bedrock (AWS) or Vertex AI Gemini (GCP). **`embedded-decide`:** one choice question scored inside this function. |
| How structure is enforced | **Vertex:** provider constrained JSON. **Bedrock (Nova default):** prompt + parse / `outputSchema` gate — Nova rejects Converse `outputConfig` (LangChain4j maps `ResponseFormat` there). **embedded-decide:** the decider returns one label; `findClass` still accepts it. |

Wrong cloud on wrong platform → `augment-gen-unavailable`. Auth / quota failures omit the augment and add a warning; the rest of the response still succeeds.

## `!<classify>`

Closed-set, **exactly one** class. YAML lists the allowed strings; there is no `outputSchema` and no `maxOutputTokens` — generation is capped at the length of the longest class, plus a small slack so the model can emit light formatting (for example `class:foo`).

If the model reply is exactly one class (or a JSON string of that class), that class is used. Otherwise a class name must appear as a whole token — not inside another word — so `A` cannot match inside `Answer`. Longer names still win when more than one matches (e.g. `Email Drafting` over `Email`). On Gemini, thinking tokens share this inferred generation budget with the visible class name.

```yaml
augments:
  - !<classify>
    jsonPaths:
      - "$..body.content"
    prompt: >
      Classify the input into exactly one class.
      Use "Uncategorized" when substantive but unclear.
      Use "Excluded" for greetings, thanks, or prompts too short to classify.
    maxInputTokens: 100
    classes:
      - Email Drafting
      - Research and Ideation
      - Uncategorized
      - Excluded
```

Output: sibling `+content:classify` with `"Research and Ideation"`.

### Object-level classify (`+self:classify`)

```yaml
augments:
  - !<classify>
    jsonPaths: ["$[*]"]
    prompt: |
      Classify this pull request. Use title and body; ignore ids and user records.
    classes: [Feature, Bugfix, Docs, Uncategorized]
transforms:
  - !<redact>
    jsonPaths: ["$[*].title", "$[*].body"]
```

- One LLM call per matched object.
- Object-level matches may send nested user/email fields to the model. Prefer prompts that tell the model to ignore ids/urls/user records, and redact source fields in `transforms` after augments.

| Field | Required | Description |
|----------|----------|-------------|
| `jsonPaths` | yes | Source values. `$` / `$[*]` → `+self:classify`; leaf paths → `+title:classify`, etc. |
| `prompt` | yes | How to choose among `classes`. |
| `classes` | yes | Allowed labels (JSON Schema enum equivalent). Output is exactly one of these strings. |
| `maxInputTokens` | no | Cap on the dynamic source corpus. Default `256`. |

## `!<genMetadata>` (structured extract)

Use this when the result is a JSON object/array, not a single class. Requires `prompt` + `outputSchema`.

### Extract example

```yaml
augments:
  - !<genMetadata>
    jsonPaths:
      - "$.timeline"
    prompt: >
      From the meeting transcript timeline, estimate seconds each person spoke.
      Identify speakers by users[].user_id from the timeline.
    maxInputTokens: 500
    maxOutputTokens: 500
    outputSchema:
      type: object
      required: [speakers]
      additionalProperties: false
      properties:
        speakers:
          type: array
          items:
            type: object
            required: [personId, secondsTalking]
            additionalProperties: false
            properties:
              personId:
                type: string
              secondsTalking:
                type: number
```

If speaker ids in the augment should be pseudonymized, add a follow-on transform on `$['+timeline:genMetadata'].speakers[*].personId`.

## Rule configuration (`!<genMetadata>`)

| Field | Required | Description |
|-------|----------|-------------|
| `jsonPaths` | yes | Source values to process. Use `$` / `$[*]` for whole objects (→ `+self:genMetadata`); use leaf paths for scalar text (→ `+title:genMetadata`, etc.). |
| `prompt` | yes | Static task guidance. Do **not** put conflicting format instructions here — Java always requests JSON and applies provider constraints from `outputSchema`. This prefix is not counted against `maxInputTokens`. |
| `outputSchema` | yes | JSON Schema for the extracted object/array. |
| `maxInputTokens` | no | Cap on the **dynamic** source corpus (serialized jsonPath match). Default `256`. Longer extract `500+`. The static prompt/schema are not counted. |
| `maxOutputTokens` | no | Per-augment **generation** cap (`maxTokens` is accepted as an alias). Default `200`. Meeting transcripts / rich extract should raise this (`500`+). On Gemini, thinking tokens share this budget with visible JSON. |

No `model`, `backend`, or `thinkingLevel` in rules — those stay deployment config. `!<classify>` YAML does not change for `embedded-decide`.

`GEN_METADATA_*` configures `!<genMetadata>` only. `!<classify>` reads `CLASSIFY_*`.

### One session attribute is one choice

An AI-session label (artifact, audience, stakes, time, AI share, flags, efficiency, opportunity) is a closed set. `embedded-decide` does not ask a model to emit a pipe-separated line. It sends one choice question: `instructions` is the rule `prompt` (the legend), and `criteria` is `classes` in order with null descriptions. Classify YAML has labels, not per-class descriptions.

```yaml
augments:
  - !<classify>
    jsonPaths: ["$..body.content"]
    prompt: >
      Who is this AI session output for?
      S self, T own team, B broad internal, L leadership or board,
      C customer, P prospect, V partner or vendor, U public.
    classes: [S, T, B, L, C, P, V, U]
```

That rule becomes:

```json
{
  "state": "<fitted serialized match>",
  "questions": {
    "label": {
      "type": "choice",
      "instructions": "Who is this AI session output for? ...",
      "criteria": { "S": null, "T": null, "B": null, "L": null, "C": null, "P": null, "V": null, "U": null }
    }
  }
}
```

The sibling property is still one string (`C`, not a record of every attribute). A second `!<classify>` on the same leaf writes the same `+content:classify` property. Stakes, time, and efficiency are choice labels (`0`/`1`/`2`, `1`–`4`), not a score question. Flags are one choice among `-`, `G`, `H`, and `GH`. A free-text name for work that is missing from the list is not a closed set, so it is not an `embedded-decide` question. Artifact codes for the usual session taxonomy (communication, documents, code, analysis, research, planning, summaries, learning, plus other) are one choice and stay under the 255-option limit.

## Deployment configuration (env)

`!<genMetadata>` is parsed from `GEN_METADATA_*` (not `ProxyConfigProperty`). `!<classify>` is parsed from `CLASSIFY_*`. Terraform `enable_gen_metadata` is IAM-only (AWS host maps it to Bedrock invoke; GCP host to Vertex user). Host Dagger modules bind Bedrock (AWS) or Vertex (GCP) for the chat path. Terraform still sets `GEN_METADATA_BACKEND` for a future non-cloud backend; it does not gate inference in Java. Model / region / thinking defaults for genMetadata live in Java. Override via `general_environment_variables` when needed.

| Variable | Default | Purpose |
|----------|---------|---------|
| `GEN_METADATA_BACKEND` | Terraform: `bedrock` (AWS) / `vertex` (GCP). | Reserved for a future backend (e.g. local). Host modules bind Bedrock vs Vertex; Java does not switch on this today. |
| `GEN_METADATA_MODEL` | AWS: `us.amazon.nova-2-lite-v1:0` · GCP: `gemini-3.5-flash-lite` | Cloud model id / **inference profile**. For Nova, use a CRIS id (`us.` / `eu.` / `jp.` / `global.` prefix) — bare `amazon.nova-…` foundation-model ids are rejected by Bedrock. If a bare `amazon.nova-…` value is set, Java rewrites it to `us.amazon.nova-…`. |
| `GEN_METADATA_MODEL_REGION` | Vertex: `global` | Vertex publisher-model **location** (`VertexGenMetadataConfig`). Default `global` (google-genai → `https://aiplatform.googleapis.com`). Ignored on AWS. |
| `GEN_METADATA_TIMEOUT_SECONDS` | `15` | Bound on wait for a worker slot plus the LLM call. Override via env. |
| `GEN_METADATA_RETRIES` | `2` | Total attempts per augment when parse/schema fails |
| `GEN_METADATA_THINKING_LEVEL` | `minimal` | **Vertex only** (`VertexGenMetadataConfig`). Gemini thinking level: `minimal` \| `low` \| `medium` \| `high`. Ignored on Bedrock. Used by `!<genMetadata>` and, on GCP, by `CLASSIFY_IMPLEMENTATION=remote-llm` (same location and thinking level; the model id still comes from `CLASSIFY_MODEL` when that is set). |

### `CLASSIFY_*`

| Variable | Default | Purpose |
|----------|---------|---------|
| `CLASSIFY_IMPLEMENTATION` | `remote-llm` | `remote-llm` or `embedded-decide`. `embedded-llm` and `remote-decide` are rejected. |
| `CLASSIFY_MODEL` | AWS: `us.amazon.nova-2-lite-v1:0` · GCP: `gemini-3.5-flash-lite` · embedded: `strands-decider-2B-qwen3.5-v1-2610` | Remote model id, or the embedded checkpoint id. Does not change `GEN_METADATA_MODEL`. A bare `amazon.nova-…` id is rewritten to the US inference profile on Bedrock only. |
| `CLASSIFY_MODEL_REVISION` | required for `embedded-decide` | Hub date tag `YYMMDD` of the tree in the artifacts bucket. `main` is refused. Ignored for `remote-llm`. |
| `CLASSIFY_TIMEOUT_SECONDS` | `15` | Per-attempt bound for both implementations. A warm embedded score is a few seconds. This does not cover model load. |
| `CLASSIFY_RETRIES` | `2` | Total attempts for both implementations. |

`embedded-decide` with a missing checkpoint or a down loopback process fails that augment (`UNAVAILABLE`). It does not fall through to `remote-llm`. Choice lists shorter than 2 or longer than 255 are rejected when that implementation is selected. Confidence is logged and not written on the sibling property.

Vertex project comes from ADC / metadata. Model location is **not** the Cloud Function region.

### Latency / model choice

Per-row Vertex/Bedrock calls are typically several seconds. A 100-row bulk file is **sequential** (one augment per matched path per row), so wall time ≈ rows × (attempts × latency). Prefer `GEN_METADATA_THINKING_LEVEL=minimal` (the default) for classify throughput.

| Model | Notes |
|-------|-------|
| `gemini-3.5-flash-lite` | **GCP default** — best for high-volume classify |
| `gemini-3.5-flash` | Better quality; set `GEN_METADATA_MODEL=gemini-3.5-flash` when needed |
| `us.amazon.nova-2-lite-v1:0` | **AWS default** — US cross-region inference profile |

## Infrastructure (Terraform)

Set `enable_gen_metadata = true` on the API, bulk, or webhook collector you want to try, and load **custom rules** that declare `!<classify>` or `!<genMetadata>` augments.

- **AWS:** Terraform attaches Bedrock invoke/converse IAM. Confirm Bedrock is usable in the account and region (see [AWS getting started](../../aws/getting-started.md#genmetadata-via-bedrock)). Default model: `us.amazon.nova-2-lite-v1:0`.
- **GCP:** Terraform enables `aiplatform.googleapis.com` and grants `roles/aiplatform.user`. Default model: `gemini-3.5-flash-lite` at location `global`, thinking `minimal`.

`!<genMetadata>` and `CLASSIFY_IMPLEMENTATION=remote-llm` do **not** need `enable_remote_resources`. `embedded-decide` does: the checkpoint is `{SHARED_RESOURCE_PATH}/decide/strands-decider-2B-qwen3.5-v1-2610/<revision>/` (adapter, head, tokenizer, and `base/` for `Qwen/Qwen3.5-2B-Base`). See [remote resources](../../configuration/remote-resources.md). Gemma checkpoints stay out of that prefix. The function loads with `HF_HUB_OFFLINE=1`. It does not download weights from Hugging Face at runtime.

The checkpoint config declares `torch_dtype` `bfloat16`. A published Lambda measurement of this container (arm64, 10,240 MB) was about 4.3 s per warm decision and a 39–49 s cold load. This repo did not re-measure resident set size. Terraform still deploys the Java zip for every connector. The zip has no local decider process, so `embedded-decide` on that zip fails the augment until the image from `tools/embedded-decide/` is what the function runs. API connectors need the model resident before the first request (Lambda provisioned concurrency, Cloud Run minimum instances). API Gateway's integration timeout is 30 s, which is shorter than the cold load. In-flight embedded calls are capped at 4 on a semaphore that belongs to that client.

### Using Anthropic Claude on Bedrock

Switching `GEN_METADATA_MODEL` to a Claude id is an advanced/expensive option. First-time Anthropic use in the account still requires a one-time use-case form (Bedrock console playground or `PutUseCaseForModelAccess`). Terraform does not submit that form.

## Java architecture

```
GenMetadataProcessor
  → GenMetadataChatModelProvider (Bedrock | Vertex), GEN_METADATA_*
ClassifyProcessor
  → remote-llm: same chat class, CLASSIFY_MODEL / CLASSIFY_TIMEOUT_SECONDS / CLASSIFY_RETRIES
  → embedded-decide: loopback POST http://127.0.0.1:8000/v1/systemone (one choice)
  → exact class / JSON string, then token-boundary match against `classes`
```

- Concurrent cloud calls use a semaphore (max 4). Embedded decisions use a separate semaphore (max 4).
- **Token usage:** each cloud call accumulates provider `input` / `output` token counts (when returned). Totals are logged at the end of each bulk file and written on the sanitized object (`psoxy-gen-metadata-input-tokens`, `psoxy-gen-metadata-output-tokens`, `psoxy-gen-metadata-calls`). GCS streams the object then PATCHes metadata; if `storage.objects.update` is missing the PATCH 403s and totals stay in logs only.
- Structured augment cells in CSV/Parquet are JSON-serialized (not Java `Map#toString()`). Rule-derived augment columns are always present; a record with no match or a failed inference gets an empty placeholder so later rows cannot drop the column.

## Error handling

| Code | Meaning |
|------|---------|
| `augment-gen-unavailable` | Unsupported/missing backend, auth deny |
| `augment-gen-inference-failed` | Call/parse failure after retries |
| `augment-output-schema-mismatch` | Post-constraint gate failed |
| `augment-conflict-skipped` | Upstream `+` properties present |

## Deferred

- **Prompt caching:** the static prefix (system text + task `prompt` + schema/labels) is stable per augment rule; the dynamic suffix is the serialized match. Provider prompt caching (Bedrock prompt cache / Vertex cached content) should pin that prefix and only pay for the capped dynamic tokens per record.
- App-layer spend ledger / infra budget alerts
- Transcript chunking / multi-call map-reduce for long inputs
- Rule-level model / thinking-level selection
- Cross-cloud backends
- Prebuilt source-rule integrations
