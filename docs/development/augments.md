# Augments — Computed Field Enrichment

> **Status:** Design · Draft
> **Since:** v0.6.x
> **Relates to:** `Transform`, `Endpoint`, `Rules2`, `JsonSchemaFilter`

## Motivation

Several use-cases require the proxy to inject *computed metadata* into the API response payload before downstream consumers see it:

| Use-case | Example |
|---|---|
| Text statistics | word count, character length |
| Keyword frequency | counts of configured keywords |
| NLP statistics | sentence structure, readability scores |
| Payload classification | labelling an LLM prompt as "email composition" vs "code generation" |

Today the `textDigest` transform **replaces** the source field's value with a nested JSON string containing the computed output (e.g. `{"length":42,"word_count":7}`). This is brittle because:

1. The original field value is destroyed — transforms that run later can no longer read it.
2. The output is *encapsulated* JSON-in-JSON rather than a first-class sibling property, making it harder for consumers to parse and schema-validate.

### Goal

Introduce **augments** — a new first-class concept in endpoint rules that:

- Add **synthetic sibling properties** alongside the source field.
- Run **before** transforms, so transforms still see original values.
- Are resilient to failures: if an augment throws or times out the synthetic property is simply omitted and a warning is logged.

---

## Naming Convention

### Augment Property Names

Inspired by OData's `@`-annotation pattern (where metadata about a property `Foo` is expressed as `Foo@odata.type`), augment output is placed in a **sibling property** at the same level in the JSON tree. We use `+` rather than `@` because it is more semantically suggestive of "additive / supplementary" and avoids collision with OData's own `@` usage in Microsoft Graph responses.

```
+{sourceProperty}:{augmentFunction}
```

| Token | Meaning |
|---|---|
| `+` | Prefix identifying the property as proxy-generated (augmented). |
| `{sourceProperty}` | The name of the field the augment reads from. |
| `:` | Separator. |
| `{augmentFunction}` | The name of the augment function (e.g. `textDigest`). |

**Example.** For a response containing `body.content`, the augment property produced by the `textDigest` function would be:

```jsonc
{
  "body": {
    "content": "Hello world …",        // original — untouched
    "+content:textDigest": {            // augment output
      "length": 42,
      "word_count": 7
    }
  }
}
```

> **Why `+`?**
> - Not a valid start-character for any known API provider's property names (Microsoft Graph uses `@`, Google uses no prefix).
> - Visually distinctive and sorts after alphanumerics in most collations.
> - Semantically suggestive of "additive / supplementary."
>
> If a future standard emerges (e.g. JSON-LD `@`-keywords), we can evolve the prefix; the `+` makes the contract explicit and easy to migrate.

### Conflict Detection

Before computing any augments for a given response object, the proxy **must** check whether the object already contains any property whose name starts with `+`. If it does:

1. Log a `WARNING` that includes the conflicting property name(s) and the request URL.
2. **Skip all augment processing** for that response object.
3. Return the response unmodified (transforms still apply).

This ensures we never silently overwrite data from the upstream API.

---

## Rule Schema

Augments are defined per-endpoint in the rules, as a new `augments` list on `Endpoint`, parallel to `transforms`.

### YAML Example

```yaml
endpoints:
  - pathTemplate: "/beta/copilot/users/{id}/interactionHistory/getAllEnterpriseInteractions"
    augments:
      - !<textDigest>
        jsonPaths:
          - "$..body.content"
        outputSchema:
          type: object
          properties:
            length:
              type: integer
            word_count:
              type: integer
      - !<textDigest>
        jsonPaths:
          - "$..attachments[*].content"
        keywords:
          - "summarize"
          - "write"
        outputSchema:
          type: object
          properties:
            length:
              type: integer
            word_count:
              type: integer
            keywords:
              type: object
    transforms:
      # transforms run AFTER augments; original field values are still intact
      - !<redact>
        jsonPaths:
          - "$..body.content"
```

### Java Model

```java
// in Endpoint.java — new field
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Singular
List<Augment> augments;
```

```java
// new class: com.avaulta.gateway.rules.augments.Augment
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "method")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Augment.TextDigest.class, name = "textDigest"),
    // future: @JsonSubTypes.Type(value = Augment.Classify.class, name = "classify"),
})
@SuperBuilder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
public abstract class Augment {

    /** JSON paths identifying source values to compute the augment from. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Singular
    List<String> jsonPaths;

    /**
     * Schema applied as a predicate to the augment's output value.
     * If the output does not conform, the augment value is dropped
     * (warning logged) but the response is otherwise unaffected.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    JsonSchemaFilter outputSchema;

    /** Produce the augment value for a single input string. */
    public abstract Object compute(String input);

    // --- Concrete subclasses ---

    @SuperBuilder(toBuilder = true)
    @NoArgsConstructor
    @Getter
    public static class TextDigest extends Augment {
        @Builder.Default List<String> keywords = new ArrayList<>();

        @Override
        public Object compute(String input) {
            // delegate to existing Transform.TextDigest.generate() logic
        }
    }
}
```

### `outputSchema` — Output Validation

Each augment rule carries an optional `outputSchema` property of type `JsonSchemaFilter`. This schema is applied as a **predicate** (not a filter) to the value produced by the augment's `compute()` method:

| Outcome | Action |
|---|---|
| Output matches schema | Augment value is added to the response. |
| Output does **not** match schema | Augment value is **discarded**; a `WARNING` is logged with the augment name, the failing property, and a description of the violation. The rest of the response (including other augments) is unaffected. |
| `outputSchema` is `null` / omitted | No validation; output is added unconditionally. |

This provides a safety net: if an augment function is updated and its output shape changes in an incompatible way, the schema prevents malformed data from leaking to consumers.

---

## Processing Order

```
  API Response
       │
       ▼
  ┌──────────────────┐
  │ Conflict Check    │  ← any existing "+" properties?
  └────────┬─────────┘
           │ (if clean)
           ▼
  ┌──────────────────┐
  │ Augments          │  ← add "+field:fn" sibling properties
  └────────┬─────────┘
           │
           ▼
  ┌──────────────────┐
  │ Response Schema   │  ← JSON schema filter (allow-list); auto-passes "+" properties
  └────────┬─────────┘
           │
           ▼
  ┌──────────────────┐
  │ Transforms        │  ← redact, pseudonymize, tokenize, …
  └────────┬─────────┘
           │
           ▼
    Sanitized Response
```

**Augments run pre-transform.** This is essential so that:

1. Transforms that *redact* the source field do not destroy the input the augment needs.
2. The augment sees the original upstream value, not an already-sanitized one.

> **Future extensions.**
> - If a use-case arises for pre-augment PII stripping, a `preAugmentTransforms` list can be added to the endpoint rules. This would run before augments and use the same transform types (pseudonymize, redact, etc.) to make data safe for augment processing.
> - If a use-case arises for post-transform augments (e.g., computing a digest of the *sanitized* output), a `postAugments` list can be added later.

### Response Schema Interaction

When augment processing is active for an endpoint, the response schema filter **automatically passes through** any `+`-prefixed properties. This works because:

1. If the raw API response contains `+`-prefixed properties, augment processing is disabled entirely (conflict detection). Those raw `+` properties are treated like any other upstream property — they must be explicitly declared in the response schema to survive filtering.
2. If no raw `+` properties exist, augment processing runs and any `+` properties in the output are guaranteed to be proxy-generated. These are automatically allowed through the schema filter without requiring explicit declaration.

This means augment authors don't need to maintain redundant schema entries for augment output properties — they are implicitly trusted since the proxy generated them.

---

## Error Handling

Augment processing is intentionally **non-fatal**:

| Failure mode | Behaviour |
|---|---|
| `compute()` throws an exception | Log `WARNING` with augment name, source path, exception. Omit the augment property. Continue. |
| `compute()` exceeds timeout | Same as exception — omit and warn. |
| Output fails `outputSchema` validation | Log `WARNING` with details. Omit the augment property. Continue. |
| Source field not present in response | No-op. (Same as current transform behaviour for missing paths.) |
| Conflict detected (`+` property exists) | Log `WARNING`. Skip **all** augments for that object. |

The proxy should **never** return a 5xx or fail the entire response because of an augment failure.

### Augment Timeout Budget

All augment processing for a single request shares a **global augment timeout** of **30 seconds** (default). This leaves headroom within the overall request `sanitizationTimeout` (55s) for transforms and serialization.

- If the augment budget is exhausted, remaining augments are skipped with a `WARNING`.
- The timeout will be configurable via environment variable `PSOXY_AUGMENT_TIMEOUT_SECONDS` (planned for a follow-up release).
- Per-augment timeouts may be introduced later if heavier augments (ML classifiers) need individual control.

---

## Migration from `textDigest` Transform

The existing `Transform.TextDigest` will be **deprecated** (not removed) in favour of `Augment.TextDigest`.

### Migration Path

| Phase | Action |
|---|---|
| **v0.6.x** | Introduce `Augment.TextDigest`. Keep `Transform.TextDigest` working as-is. Log a deprecation notice when the transform variant is used. |
| **v0.7.x** | Remove `Transform.TextDigest`. All prebuilt rules updated to use `augments:` instead. |

### Prebuilt Rule Changes (PoC)

For the initial PoC, update the MS Copilot rules to move `textDigest` from `transforms` to `augments`:

**Before (transform — replaces field value):**
```yaml
transforms:
  - !<textDigest>
    jsonPaths:
      - "$..body.content"
```

**After (augment — adds sibling property):**
```yaml
augments:
  - !<textDigest>
    jsonPaths:
      - "$..body.content"
    outputSchema:
      type: object
      properties:
        length:
          type: integer
        word_count:
          type: integer
transforms:
  - !<redact>
    jsonPaths:
      - "$..body.content"
```

The consumer now sees:
```jsonc
{
  "body": {
    // "content" is redacted by the transform
    "+content:textDigest": {
      "length": 314,
      "word_count": 52
    }
  }
}
```

---

## Implementation Checklist

1. **`Augment` base class + `Augment.TextDigest`** in `gateway-core` (`com.avaulta.gateway.rules.augments`)
2. **`augments` field on `Endpoint`** — new `List<Augment>`, with Jackson polymorphic deserialization.
3. **`AugmentProcessor`** in `core` — applies augments to a Jayway JSONPath document:
   - Conflict check (scan for `+` prefixed properties).
   - For each augment × each jsonPath: resolve parent node, compute, validate schema, insert `+{prop}:{fn}`.
   - 30s global timeout budget across all augments per request.
4. **Wire into `RESTApiSanitizerImpl.sanitize(Endpoint, Object)`** — new processing order: `AugmentProcessor → responseSchema (auto-pass + properties) → transforms`.
5. **Update `JsonSchemaFilterUtils`** — when augments are active, auto-pass `+`-prefixed properties through the response schema filter.
6. **Wire into `RecordBulkDataSanitizerImpl`** / bulk pipelines — augments apply to record-oriented bulk data. For columnar (CSV) output, augment columns are named `+{column}:{fn}` and appended after the source column.
7. **Update prebuilt rules** (MS Copilot as PoC) to use `augments:`.
8. **Deprecate `Transform.TextDigest`** — add `@Deprecated` annotation and log notice.
9. **Tests:**
   - Unit: `AugmentProcessor` — happy path, conflict detection, schema validation failure, compute exception, timeout.
   - Unit: response schema auto-pass — verify `+` properties survive filtering without explicit schema declaration.
   - Integration: end-to-end sanitization with augments + transforms on sample MS Copilot payloads.
10. **Documentation** — this file + update `docs/configuration/` if needed.

---

## Resolved Design Decisions

| Question | Decision | Rationale |
|---|---|---|
| Bulk / columnar data | **Yes, extend.** Augments apply to `RecordRules` and `ColumnarRules`. For CSV output, augment columns are named `+{column}:{fn}`. | Augments are a general enrichment concept, not API-specific. |
| `outputSchema` semantics | **Predicate** (pass/fail gate). | Augment implementations should be precise; silent stripping masks bugs. A `filterOutput: true` option can be added later. |
| Augment timeouts | **Global 30s budget** for all augments per request. | Leaves headroom within the 55s request timeout. Configurable via `PSOXY_AUGMENT_TIMEOUT_SECONDS` env var in a follow-up. Per-augment timeouts deferred until heavier augments exist. |
| Pre- vs. post-transform | **Pre-transform default.** | Augments need original field values. `preAugmentTransforms` and/or `postAugments` can be added as separate endpoint rule lists later if use-cases arise. |
| PII in augment inputs | **Deferred.** Not addressed in PoC. | Most current augments (textDigest) operate on content fields and don't need PII stripped first. When a use-case arises (e.g. classifier on a field with inline PII), add `preAugmentTransforms` to rules. |
| Response schema + augments | **Auto-pass `+` properties.** | Since `+` in raw data disables augments (conflict check), any `+` properties post-augment are guaranteed proxy-generated and safe to pass through the schema filter without explicit declaration. |
| `isJsonEscaped` / `jsonPathToProcessWhenEscaped` | **Omitted from PoC.** | YAGNI — the existing `Transform.TextDigest` supports this for Copilot AdaptiveCard payloads (JSON-in-JSON), but the augment PoC doesn't need it. Can be added later if a clear use-case emerges for computing augments from sub-components of escaped JSON values. |
