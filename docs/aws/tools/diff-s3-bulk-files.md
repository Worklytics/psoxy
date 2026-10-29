# Diff S3 bulk files (input vs sanitized)

**Script:** [`diff-s3-bulk-files.sh`](https://github.com/Worklytics/psoxy/blob/main/tools/aws/diff-s3-bulk-files.sh) · [Download raw](https://raw.githubusercontent.com/Worklytics/psoxy/main/tools/aws/diff-s3-bulk-files.sh)

Single self-contained script; no other files required.

Lists objects in a bulk connector **input** bucket that do **not** have a corresponding object in the **sanitized** output bucket—within a time window. Output is sorted with the **newest input objects first**, which helps when investigating recent pipeline failures.

Download the script directly from GitHub; a full Psoxy checkout is not required.

## Requirements

- AWS CLI authenticated
- `s3:ListBucket` on the input bucket
- `s3:HeadObject` (or `s3:GetObject`) on the sanitized bucket
- Listing uses the AWS CLI only (`jq` optional, for faster JSON parsing when installed)

## Usage

```bash
chmod +x diff-s3-bulk-files.sh

# Default: sanitized bucket = input name with -input → -sanitized; lookback = 7 days
./diff-s3-bulk-files.sh myenv-connector-input

# Explicit sanitized bucket and cutoff time
./diff-s3-bulk-files.sh myenv-connector-input myenv-connector-sanitized 2024-06-01T00:00:00Z

# Only keys under a prefix
./diff-s3-bulk-files.sh --prefix exports/ myenv-connector-input

# Match Psoxy INPUT_BASE_PATH / OUTPUT_BASE_PATH when keys differ between buckets
./diff-s3-bulk-files.sh \
  --input-base-path raw/ \
  --output-base-path sanitized/ \
  myenv-connector-input

./diff-s3-bulk-files.sh --role arn:aws:iam::123456789012:role/BulkDebugger myenv-connector-input
```

## How keys are compared

By default, the script expects the sanitized object key to equal the input object key (typical Psoxy bulk module layout). If your connector sets `INPUT_BASE_PATH` or `OUTPUT_BASE_PATH`, pass the same values with `--input-base-path` and `--output-base-path` so expected sanitized keys match what the proxy writes.

## Exit codes

- **0** — no missing objects in the window (or no input objects in the window)
- **1** — one or more input objects lack a sanitized counterpart (details printed to stdout)

## Related tools

- [Replay S3 writes](replay-s3-writes.md) — re-trigger processing for input objects after you fix the underlying error.
- [AWS troubleshooting — bulk processing failures](../troubleshooting.md#bulk-processing-failures)
