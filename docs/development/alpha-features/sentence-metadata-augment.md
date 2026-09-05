# NLP Sentence Structure Augment (ALPHA)

> **Status: ALPHA / Proof of Concept**
> This NLP feature is a proposed half-solution for users who want to transmit sophisticated metadata to support better client-side analysis, but prefer not to perform LLM-powered analysis directly within the proxy. Much of the structural analysis and pattern detection achievable here with OpenNLP could alternatively be achieved with LLMs.

## Overview
We are building an augment that performs NLP analysis on text fields in API requests/responses, with the primary use case being the analysis of LLM prompts. The output is a `+{field}.sentenceMetadata` metadata block attached to the API payload.

## Use Cases
The primary use cases include analyzing LLM prompts to understand their structure and semantic elements, extracting insights from text fields in API payloads without exposing sensitive raw text data, and identifying trends in prompt structure such as the use of imperatives, constraints, or hedges.

## Output Schema
The `+sentenceMetadata` block is structured with a `sentences` array containing one object per detected sentence, and a `doc_summary` object containing net-new aggregations not derivable from a simple flatten of the sentence list.

### Annotated JSON Example

```json
{
  "+prompt.sentenceMetadata": {
    "sentences": [
      {
        "index": 0,
        "type": "imperative", // (imperative/declarative/interrogative)
        "verbs": [
          {
            "verb": "write", // surface form
            "pos": "VB", // Penn Treebank tag
            "is_modal": false,
            "is_negated": false,
            "is_auxiliary": false
          }
        ],
        "nouns": [
          {
            "noun": "code",
            "category": "CODE_ARTIFACT", // from taxonomy
            "np_head": true,
            "np_position": "object" // (subject/object)
          }
        ],
        "modifiers": [
          "Python",
          "efficiently"
        ], // adjectives and adverbs as flat string arrays
        "structure": {
          "voice": "active", // (active/passive)
          "vp_count": 1,
          "np_count": 1
        },
        "signals": {
          "hedged": false,
          "constraint": false,
          "question": false,
          "negated": false
        },
        "suppressed": {
          "common_nouns": 0, // Unmatched common nouns are suppressed and counted only
          "proper_nouns": 0 // Proper nouns (NNP) are always suppressed
        }
      }
    ],
    "doc_summary": {
      "sentence_count": 1,
      "token_count": 3,
      "sentence_types": {
        "imperative": 1
      },
      "noun_categories": [
        "CODE_ARTIFACT"
      ], // deduplicated set
      "suppressed": {
        "common_nouns": 0,
        "proper_nouns": 0
      }, // totals
      "any_hedged": false,
      "any_constraint": false,
      "any_question": false,
      "any_negated": false
    }
  }
}
```

## NLP Pipeline
The NLP processing will rely exclusively on Apache OpenNLP. We use pretrained English models: `en-sent.bin`, `en-pos-maxent.bin`, and `en-chunker.bin`. No sentiment analysis will be performed.

### Performance, Size, and Deployment Strategy
- **Library Choice**: Apache OpenNLP is chosen over alternatives like Stanford CoreNLP due to its smaller footprint. The OpenNLP core library is very lightweight (~1.5 MB), and the combined pre-trained models require approximately 15-20 MB. This keeps the total deployment package size well within AWS Lambda and GCP Cloud Function unzipped limits (which CoreNLP's ~500 MB footprint would exceed).
- **Memory Consumption**: When loaded, the models will consume roughly 50-150 MB of heap memory.
- **Lazy Loading Strategy**: To prevent unnecessary memory consumption and ensure zero impact on proxies that do not utilize the `SentenceMetadata` augment, the implementation must load models *lazily*. The models should only be read into memory the first time the augment is invoked at runtime. If the augment is unused, memory overhead is 0 MB.
- **Deployment & Embedding**: 
  - For local development, testing, and CI, the `.bin` models can be loaded from the classpath (e.g. `src/main/resources/opennlp/`) after running `tools/fetch-opennlp-models.sh`.
  - **Deployment bundles exclude model binaries.** The shaded AWS/GCP JARs do not contain `opennlp/*.bin` files; only the `opennlp-tools` library is included.
  - **Remote Loading Architecture**: In cloud deployments, the proxy loads models on demand at runtime via `ResourceService`. Models are resolved at `opennlp/{model}.bin` under the shared resource path (`SHARED_RESOURCE_PATH` in the remote bucket). The JVM streams binaries directly into memory via `InputStream`.
  - **Uploading models**: Customers must download and upload model binaries to the remote resources bucket. Use `./tools/fetch-opennlp-models.sh s3://BUCKET/PREFIX/` or `gs://BUCKET/PREFIX/` (see [remote-resources.md](../configuration/remote-resources.md)).
  - **Future Terraform option**: We may later offer a small, optional Terraform module (invoked at the top level of an example deployment, not wired into `aws-host` / `gcp-host`) that downloads and uploads the model binaries to the remote resources bucket. That follows proper composition—customers who do not use `sentenceMetadata` augments can omit the module entirely, and teams with lint/policy restrictions on `local-exec` provisioners can leave it out without affecting core host modules.
  - *Note on Future Extensibility*: This architecture—allowing heavy machine learning artifacts to be embedded or loaded remotely via GCS/S3—will serve as the foundational pattern for future local LLM integrations, allowing model weights to be fetched on demand or delegated to external cloud services (Vertex AI, Amazon Bedrock).
## Noun Taxonomy Design
Nouns are only emitted if matched against a configurable taxonomy of safe semantic classes (e.g. `CONTENT_TYPE`, `CODE_ARTIFACT`, `TASK_NOUN`, `FORMAT`, `MEDIUM`). The taxonomy must be externally configurable via a JSON or YAML config file, and not hardcoded. Proper nouns (NNP) are always suppressed. Unmatched common nouns are suppressed and counted.

## Derived Signals
Signals will be derived using linguistic patterns rather than requiring a comprehensive keyword list. Sentence type is derived from POS patterns (imperative = VP-initial, interrogative = question mark + inversion). Voice is derived from the VBN + be-verb pattern in VP chunks. Negation is derived from RB "not" / "n't" tokens adjacent to a verb. Hedge detection is based on a small closed-class adverb list (maybe, perhaps, kind of, sort of, probably, somewhat). Constraint detection is based on a small closed-class list (must, only, never, always, don't, avoid, require). The `np_position` is approximated from NP chunk position relative to VP chunk (pre-VP = subject, post-VP = object).

## Privacy Model
Privacy and data sensitivity are the primary considerations for this augment. All raw noun text is suppressed unless matched to the safe taxonomy. Verbs are always safe to emit as surface forms, so no suppression is applied. Adjectives and adverbs are emitted as flat lists as they carry low PII risk. Proper adjectives (JJP) should be flagged but not suppressed. Additionally, the proxy should be able to annotate multiple text fields independently to support per-field analysis.

## Open Questions
- How should the external configuration file for the noun taxonomy be distributed and loaded by the proxy?
- Are there edge cases in `np_position` approximation that need custom handling (e.g. complex sentences with multiple clauses)?
- What is the expected performance overhead of running the OpenNLP pipeline on large payloads, and should we enforce timeout or length thresholds?
