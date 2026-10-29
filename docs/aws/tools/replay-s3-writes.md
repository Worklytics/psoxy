# Replay S3 writes (bulk debugging)

**Script:** [`replay-s3-writes.sh`](https://github.com/Worklytics/psoxy/blob/main/tools/aws/replay-s3-writes.sh) · [Download raw](https://raw.githubusercontent.com/Worklytics/psoxy/main/tools/aws/replay-s3-writes.sh)

Single self-contained script; no other files required.

Re-triggers bulk processing for objects already in a bulk connector **input** S3 bucket. The script copies each object to itself and sets `psoxy-last-replay` metadata, which emits an S3 `ObjectCreated` event so the Psoxy Lambda runs again.

You do not need a full clone of the Psoxy repository; download the script and run it with AWS CLI credentials that can read and write the bucket.

## Requirements

- [AWS CLI](https://aws.amazon.com/cli/) authenticated (`aws sts get-caller-identity` succeeds)
- IAM permissions on the target bucket: `s3:ListBucket`, `s3:GetObject`, `s3:GetObjectTagging`, `s3:PutObject`, `s3:PutObjectTagging`
- Listing works with AWS CLI alone; merging existing object metadata for replay requires **jq** or **Node.js** (unless the object has no prior metadata)

## Usage

```bash
chmod +x replay-s3-writes.sh

# Objects modified in the last 7 days (default lookback)
./replay-s3-writes.sh myenv-connector-input

# Objects modified since a specific time (ISO 8601, UTC)
./replay-s3-writes.sh myenv-connector-input 2024-06-01T00:00:00Z

# Limit to a key prefix
./replay-s3-writes.sh --prefix exports/ myenv-connector-input

# Single object
./replay-s3-writes.sh s3://myenv-connector-input/path/to/file.ndjson

# Assume a role first (common in multi-account setups)
./replay-s3-writes.sh --role arn:aws:iam::123456789012:role/BulkDebugger myenv-connector-input
```

## Behavior

- **Bucket mode:** lists objects in the input bucket whose **LastModified** is after the cutoff (default: one week ago), then replays each.
- **Single-object mode:** pass a path that includes `/` after the bucket name (`s3://bucket/key` or `bucket/key`).
- Existing object content, tags, and metadata are preserved; only metadata is updated to include `psoxy-last-replay`.

## Related tools

- [Diff S3 bulk files](diff-s3-bulk-files.md) — find input objects with no matching file in the sanitized bucket before replaying.
- [AWS troubleshooting — bulk processing failures](../troubleshooting.md#bulk-processing-failures)
