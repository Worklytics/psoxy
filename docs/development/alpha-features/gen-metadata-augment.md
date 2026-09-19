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
| Runtime | **Cloud only** — Amazon Bedrock (AWS) or Vertex AI Gemini (GCP) |
| How structure is enforced | **Vertex:** provider constrained JSON. **Bedrock (Nova default):** prompt + parse / `outputSchema` gate — Nova rejects Converse `outputConfig` (LangChain4j maps `ResponseFormat` there). |

Wrong cloud on wrong platform → `augment-gen-unavailable`. Auth / quota failures omit the augment and add a warning; the rest of the response still succeeds.

## `!<classify>`

Closed-set, **exactly one** class. YAML lists the allowed strings; there is no `outputSchema` and no `maxOutputTokens` — generation is capped at the length of the longest class.

If the model reply contains any of those class names as an exact substring, that class is used (longer names win when more than one matches, e.g. `Email Drafting` over `Email`). On Gemini, thinking tokens share this inferred generation budget with the visible class name.

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
                minimum: 0
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

No `model`, `backend`, or `thinkingLevel` in rules — those stay deployment config.

## Deployment configuration (env)

Parsed from `GEN_METADATA_*` env vars (not `ProxyConfigProperty`). Terraform `enable_gen_metadata` is IAM-only (Bedrock invoke / Vertex user). Host Dagger modules bind Bedrock (AWS) or Vertex (GCP). Terraform still sets `GEN_METADATA_BACKEND` for a future non-cloud backend; it does not gate inference in Java. Model / region / thinking defaults live in Java. Override via `general_environment_variables` when needed.

| Variable | Default | Purpose |
|----------|---------|---------|
| `GEN_METADATA_BACKEND` | Terraform: `bedrock` (AWS) / `vertex` (GCP). | Reserved for a future backend (e.g. local). Host modules bind Bedrock vs Vertex; Java does not switch on this today. |
| `GEN_METADATA_MODEL` | AWS: `us.amazon.nova-2-lite-v1:0` · GCP: `gemini-3.5-flash-lite` | Cloud model id / **inference profile**. For Nova, use a CRIS id (`us.` / `eu.` / `jp.` / `global.` prefix) — bare `amazon.nova-…` foundation-model ids are rejected by Bedrock. If a bare `amazon.nova-…` value is set, Java rewrites it to `us.amazon.nova-…`. |
| `GEN_METADATA_MODEL_REGION` | Vertex: `global` | Vertex publisher-model **location** (`VertexGenMetadataConfig`). Default `global` (google-genai → `https://aiplatform.googleapis.com`). Ignored on AWS. |
| `GEN_METADATA_TIMEOUT_SECONDS` | `15` | Per-call timeout |
| `GEN_METADATA_RETRIES` | `2` | Total attempts per augment when parse/schema fails |
| `GEN_METADATA_THINKING_LEVEL` | `minimal` | **Vertex only** (`VertexGenMetadataConfig`). Gemini thinking level: `minimal` \| `low` \| `medium` \| `high`. Ignored on Bedrock. |

Vertex project comes from ADC / metadata. Model location is **not** the Cloud Function region.

### Latency / model choice

Per-row Vertex/Bedrock calls are typically several seconds. A 100-row bulk file is **sequential** (one augment per matched path per row), so wall time ≈ rows × (attempts × latency). Prefer `GEN_METADATA_THINKING_LEVEL=minimal` (the default) for classify throughput.

| Model | Notes |
|-------|-------|
| `gemini-3.5-flash-lite` | **GCP default** — best for high-volume classify |
| `gemini-3.5-flash` | Better quality; set `GEN_METADATA_MODEL=gemini-3.5-flash` when needed |
| `us.amazon.nova-2-lite-v1:0` | **AWS default** — US cross-region inference profile |

## Infrastructure (Terraform)

Set `enable_gen_metadata = true` on the API or bulk connector you want to try, and load **custom rules** that declare `!<classify>` or `!<genMetadata>` augments.

- **AWS:** Terraform attaches Bedrock invoke/converse IAM. Complete the `gen_metadata_todo` output (Bedrock account/region access). Default model: `us.amazon.nova-2-lite-v1:0`.
- **GCP:** Terraform enables `aiplatform.googleapis.com` and grants `roles/aiplatform.user`. Default model: `gemini-3.5-flash-lite` at location `global`, thinking `minimal`.

genMetadata does **not** need `enable_remote_resources` or `REMOTE_RESOURCE_BUCKET`. Those are for remote `rules.yaml` / OpenNLP (`sentenceMetadata`).

### Using Anthropic Claude on Bedrock

Switching `GEN_METADATA_MODEL` to a Claude id is an advanced/expensive option. First-time Anthropic use in the account still requires a one-time use-case form (Bedrock console playground or `PutUseCaseForModelAccess`). Terraform does not submit that form.

## Java architecture

```
ClassifyProcessor / GenMetadataProcessor
  → GenMetadataChatModelProvider (Bedrock | Vertex)
  → classify: exact substring match against `classes`
  → genMetadata: JSON parse → outputSchema gate
```

- Concurrent cloud calls use a semaphore (max 4).
- **Token usage:** each cloud call accumulates provider `input` / `output` token counts (when returned). At end of each bulk file, totals are logged once and written on the sanitized object:
  - `psoxy-gen-metadata-input-tokens`
  - `psoxy-gen-metadata-output-tokens`
  - `psoxy-gen-metadata-calls`
- Structured augment cells in CSV/Parquet are JSON-serialized (not Java `Map#toString()`).

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
