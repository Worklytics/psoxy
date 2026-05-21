# Workdata Generic Connector

**Connector ID:** `workdata-generic`

This connection type allows exporting pseudonymized metadata from generic workloads or custom self-hosted applications using standard JSON payloads (NDJSON format). Since this is an off-the-shelf bulk import connection, data must be pushed into an S3/GCS bucket that Psoxy will process.

To enable this connector in one of our examples, add `workdata-generic` to `enabled_connectors` in your `terraform.tfvars` file.

## File Path Schema & Compression
The connector enforces strict directory schemas incorporating an export identifier and a shard index (e.g., `events0`, `events1`). Files **must** match the following formats in your bucket:
- `/{exportId}/events{shardIndex}.ndjson` (e.g. `/export-20231128/events0.ndjson`)
- `/{exportId}/items{shardIndex}.ndjson`
- `/{exportId}/accounts{shardIndex}.ndjson`

> [!NOTE] 
> Empty shard indexes are not supported. If you only have one file, simply use `0` as the index (e.g., `events0.ndjson`).

> [!IMPORTANT] 
> Suffixes like `.gz` inside the filepath schema are deliberately NOT supported. If your files are compressed with gzip (which is highly recommended), you must keep the `.ndjson` filename extension and properly set the HTTP remote metadata `Content-Encoding: gzip` and `Content-Type: application/x-ndjson` upon upload. Psoxy will natively detect this and stream it transparently.

## Examples

- [Example Rules](workdata-generic.yaml)
- Example Data (Events): [events0.ndjson](example-bulk/original/events0.ndjson) | [sanitized](example-bulk/sanitized/events0.ndjson)
- Example Data (Items): [items0.ndjson](example-bulk/original/items0.ndjson) | [sanitized](example-bulk/sanitized/items0.ndjson)
- Example Data (Accounts): [accounts0.ndjson](example-bulk/original/accounts0.ndjson) | [sanitized](example-bulk/sanitized/accounts0.ndjson)

Please see the full documentation: [Connecting to Worklytics](https://docs.worklytics.co/psoxy/sources/workdata-generic).
