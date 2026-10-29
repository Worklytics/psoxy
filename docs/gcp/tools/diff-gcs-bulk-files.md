# Diff GCS bulk files (input vs sanitized)

**Script:** [`diff-gcs-bulk-files.sh`](https://github.com/Worklytics/psoxy/blob/main/tools/gcp/diff-gcs-bulk-files.sh) · [Download raw](https://raw.githubusercontent.com/Worklytics/psoxy/main/tools/gcp/diff-gcs-bulk-files.sh)

Single self-contained script; no other files required.

Lists objects in a bulk connector **input** bucket that do **not** have a corresponding object in the **sanitized** bucket, limited to a time window. Results are printed **newest first**.

## Requirements

- Google Cloud SDK (`gsutil`, authenticated)
- `storage.objects.list` on the input bucket and `storage.objects.get` on the sanitized bucket (for example `roles/storage.objectViewer` on both)

## Usage

```bash
chmod +x diff-gcs-bulk-files.sh

# Default sanitized bucket name (-input → -sanitized) and 7-day lookback
./diff-gcs-bulk-files.sh myenv-connector-input

# Explicit sanitized bucket and cutoff
./diff-gcs-bulk-files.sh myenv-connector-input myenv-connector-sanitized 2024-06-01T00:00:00Z

./diff-gcs-bulk-files.sh --prefix exports/ myenv-connector-input

./diff-gcs-bulk-files.sh \
  --input-base-path raw/ \
  --output-base-path sanitized/ \
  myenv-connector-input
```

## Key mapping

Same rules as the AWS tool: by default sanitized keys match input keys. Use `--input-base-path` and `--output-base-path` when your connector sets the corresponding Psoxy environment variables.

## Exit codes

- **0** — no missing objects in the window
- **1** — at least one input object has no sanitized counterpart

## Related tools

- [Replay GCS writes](replay-gcs-writes.md)
- [GCP troubleshooting — bulk processing failures](../troubleshooting.md#bulk-processing-failures)
