# GCP tools

Command-line scripts for debugging and operating Psoxy bulk connectors on GCP. Scripts live under [`tools/gcp/`](https://github.com/Worklytics/psoxy/tree/main/tools/gcp) in the repository.

## Bulk debugging

- [Diff GCS bulk files](diff-gcs-bulk-files.md) — find input objects with no sanitized output
- [Replay GCS writes](replay-gcs-writes.md) — re-trigger Cloud Function processing via GCS events

`diff-gcs-bulk-files.sh` and `replay-gcs-writes.sh` source [`gcs-bulk-tool-common.sh`](https://raw.githubusercontent.com/Worklytics/psoxy/main/tools/gcp/gcs-bulk-tool-common.sh); keep that file in the same directory when downloading from GitHub.

See also [GCP troubleshooting — bulk processing failures](../troubleshooting.md#bulk-processing-failures).
