# regexExtract Augment (ALPHA)

> **Status:** ALPHA
> **Relates to:** [augments.md](../augments.md)

## Overview

**regexExtract** applies ordered regex rules per output field on a matched string (or on text concatenated from object fields). The first matching rule per field wins; fields with no match are omitted from the augment output.

Prebuilt connector rules do **not** enable this augment yet. Example YAML for a GitHub AI-authorship PoC lives in test resources and is exercised by `RegexExtractAiAttributionAugmentTest`.

| Output shape | When |
|---|---|
| `+{field}:regexExtract` | Leaf match (e.g. `commit.message`) |
| `+self:regexExtract` | Object match (`$`, `$[*]`) with optional `sourceFields` |

## Rule shape

```yaml
augments:
  - !<regexExtract>
    jsonPaths:
      - "$..commit.message"
    extractions:
      aiAssistPlatform:
        - regex: "(?i)Made with Cursor"
          value: "Cursor"
        - regex: "(?i)Co-Authored-By:\\s*Cursor"
          value: "Cursor"
      aiAssistModel:
        - regex: "(?i)model:\\s*([\\w./+-]+)"
          group: 1
      commitType:
        - regex: "(?i)^(?:fix|bugfix)(?:\\(|:|\\s)"
          value: "fix"
```

Each extraction entry is either:

- **`value`** — literal assigned when `regex` matches
- **`group`** — 1-based capture group to extract (default `1` when `value` is omitted)

## GitHub AI authorship PoC (example only)

Use-case: attribute commits and PRs to declared AI assistance (Cursor, Claude Code, Copilot, etc.) and classify conventional commit / PR intent — **message-only**, measuring declared trailers rather than author login.

| Field | Source | Notes |
|---|---|---|
| `aiAssistPlatform` | `commit.message`, PR `title`/`body`/`commit_message` | Cursor, Claude Code, Copilot, Other AI; omitted when no trailer |
| `aiAssistModel` | same | From `model:` lines or similar; omitted when unknown |
| `commitType` | `commit.message` | `fix`, `feature`, `docs`, `other` from conventional prefixes |
| `prCategory` | PR fields | `Bugfix`, `Feature`, `Docs`, `Other` |

**Limitation:** Cursor and Claude allow turning attribution off; those commits look human. Cloud Cursor may author as `Cursor Agent` on the author field — that is a later deterministic check, not covered by message regex alone.

### Example files (tests)

Canonical YAML (not shipped in prebuilt GitHub rules):

- `java/core/src/test/resources/alpha-features/github-ai-attribution/commit-message-regex-extract.yaml`
- `java/core/src/test/resources/alpha-features/github-ai-attribution/pull-request-regex-extract.yaml`
- `java/core/src/test/resources/alpha-features/github-ai-attribution/example-repo-commits.json`

Run: `mvn test -pl core -Dtest=RegexExtractAiAttributionAugmentTest`

### Pull request augment (object-level)

```yaml
augments:
  - !<regexExtract>
    jsonPaths:
      - "$[*]"
    sourceFields:
      - "title"
      - "body"
      - "commit_message"
    extractions:
      prCategory: [...]
      aiAssistPlatform: [...]
      aiAssistModel: [...]
```

Augments run **before** transforms, so `message` / `body` can be redacted afterward while `+message:regexExtract` / `+self:regexExtract` remain.
