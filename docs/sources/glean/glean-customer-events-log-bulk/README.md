# Glean Customer Event Logs (Bulk)

**Connector ID:** `glean-customer-events-log-bulk`

**Availability:** Beta

This bulk connector sanitizes Glean Customer Event (GCE) logs delivered to object storage (for example, Google Cloud Storage or Amazon S3). By default, Glean writes a unified `glean-customer-event` log stream containing structured records of searches, clicks, chats, workflow runs, and other product analytics events.

See Glean's documentation for the authoritative schema:

- [GCE Logs Overview](https://docs.glean.com/administration/gce-logs/data-dictionary)
- [GCE Logs Detailed Schema](https://docs.glean.com/administration/gce-logs/data-dictionary-detailed)

## Sanitization Strategy

Rules apply to **all files** in the proxy input bucket (no path-template filter). Each NDJSON line is processed as follows:

1. **Output schema filter** (`outputSchemaFilter`) — only documented GCE envelope and payload fields are retained in the output. Unknown event types or undeclared nested fields (for example, a new `FuturePayload` block) are dropped after transforms run.
2. **Transforms** — known sensitive fields that are intentionally retained for analytics are sanitized in place:
   - **Pseudonymize** — `UserEmail`, `UserId`, department names, and product-snapshot user identifiers
   - **Tokenize** — document IDs and URLs (reversible tokens for cross-event joins within your proxy deployment)
   - **Text digest** — queries, titles, chat text, comments, and other free-text (replaced with length/word-count metadata)

## Examples

- [Example Rules](glean-customer-events-log-bulk.yaml)
- Example Data: [original/sample.ndjson](example-bulk/original/sample.ndjson) | [sanitized/sample.ndjson](example-bulk/sanitized/sample.ndjson)

## Setup

### Prerequisites

- Glean Customer Event logging enabled and delivered to a bucket you control (or a Firehose/Sink destination you can route through Psoxy)
- Files should be **gzip-compressed** with `Content-Encoding: gzip` for production throughput (see [bulk sanitization docs](../../configuration/bulk-file-sanitization.md))

### Configuration

Enable the connector in your Terraform configuration:

```hcl
enabled_connectors = [
  # ...
  "glean-customer-events-log-bulk",
]
```

Copy sanitized output from the proxy `-sanitized` bucket to Worklytics per your bulk import pipeline.
