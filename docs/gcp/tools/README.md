# GCP tools

Command-line scripts for debugging and operating Psoxy bulk connectors on GCP. Scripts live under [`tools/gcp/`](https://github.com/Worklytics/psoxy/tree/main/tools/gcp) in the repository.

## Bulk debugging

- [Diff GCS bulk files](diff-gcs-bulk-files.md) — find input objects with no sanitized output
- [Replay GCS writes](replay-gcs-writes.md) — re-trigger Cloud Function processing via GCS events

Each script is standalone (one file to download from GitHub).

See also [GCP troubleshooting — bulk processing failures](../troubleshooting.md#bulk-processing-failures).
