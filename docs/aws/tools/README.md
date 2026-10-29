# AWS tools

Command-line scripts for debugging and operating Psoxy bulk connectors on AWS. Scripts live under [`tools/aws/`](https://github.com/Worklytics/psoxy/tree/main/tools/aws) in the repository.

## Bulk debugging

- [Diff S3 bulk files](diff-s3-bulk-files.md) — find input objects with no sanitized output
- [Replay S3 writes](replay-s3-writes.md) — re-trigger Lambda processing via S3 events

Each script is standalone (one file to download from GitHub).

See also [AWS troubleshooting — bulk processing failures](../troubleshooting.md#bulk-processing-failures).
