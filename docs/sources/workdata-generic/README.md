# Workdata Generic Connector

This connection type allows exporting pseudonymized metadata from generic workloads or custom self-hosted applications using standard JSON payloads (NDJSON format). Since this is an off-the-shelf bulk import connection, data must be pushed into an S3/GCS bucket that Psoxy will process.

To enable this connector in one of our examples, add `workdata-generic` to `enabled_connectors` in your `terraform.tfvars` file.

## Examples

- [Example Rules](workdata-generic.yaml)
- Example Data (Events): [events.ndjson](example-bulk/original/events.ndjson) | [sanitized](example-bulk/sanitized/events.ndjson)
- Example Data (Items): [items.ndjson](example-bulk/original/items.ndjson) | [sanitized](example-bulk/sanitized/items.ndjson)
- Example Data (Accounts): [accounts.ndjson](example-bulk/original/accounts.ndjson) | [sanitized](example-bulk/sanitized/accounts.ndjson)

Please see the full documentation: [Connecting to Worklytics](https://docs.worklytics.co/psoxy/sources/workdata-generic).
