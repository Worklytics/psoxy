# Replay GCS writes (bulk debugging)

**Script:** [`replay-gcs-writes.sh`](https://github.com/Worklytics/psoxy/blob/main/tools/gcp/replay-gcs-writes.sh) · [Download raw](https://raw.githubusercontent.com/Worklytics/psoxy/main/tools/gcp/replay-gcs-writes.sh)

Also download [`gcs-bulk-tool-common.sh`](https://raw.githubusercontent.com/Worklytics/psoxy/main/tools/gcp/gcs-bulk-tool-common.sh) into the same directory.

Re-triggers bulk processing for objects already in a bulk connector **input** Cloud Storage bucket. The script runs `gsutil rewrite -kO` on each object, which emits an object update event so the Psoxy Cloud Function processes the file again.

Download the script from GitHub; you do not need to clone the full repository.

## Requirements

- [Google Cloud SDK](https://cloud.google.com/sdk) (`gsutil`, `gcloud auth login`)
- Permissions on the bucket: `storage.objects.list`, `storage.objects.get`, `storage.objects.update` (for example `roles/storage.objectAdmin`)

## Usage

```bash
chmod +x replay-gcs-writes.sh

# Objects updated in the last 7 days (default lookback)
./replay-gcs-writes.sh myenv-connector-input

# Objects updated since a specific time (ISO 8601, UTC)
./replay-gcs-writes.sh myenv-connector-input 2024-06-01T00:00:00Z

# Limit to a key prefix
./replay-gcs-writes.sh --prefix exports/ myenv-connector-input

# Single object
./replay-gcs-writes.sh gs://myenv-connector-input/path/to/file.ndjson
```

## Behavior

- **Bucket mode:** lists objects updated after the cutoff (default: one week ago), then rewrites each in place.
- **Single-object mode:** pass `gs://bucket/object` or `bucket/object`.

## Related tools

- [Diff GCS bulk files](diff-gcs-bulk-files.md) — find input objects missing from the sanitized bucket.
- [GCP troubleshooting — bulk processing failures](../troubleshooting.md#bulk-processing-failures)
