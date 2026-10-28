# aws-psoxy-bulk

This module provisions an AWS Lambda that sanitizes files uploaded into an input bucket, and writes the sanitized files to an output bucket.

Optional variables `file_prefix` and `file_suffix` restrict which S3 `ObjectCreated` events invoke the Lambda (S3 notification filters). Set `file_pattern` instead to use EventBridge (wildcard on object key); do not combine `file_pattern` with prefix/suffix.

This module provisions the Lambda + both buckets. If you have an existing input bucket you want to
use, see [`../aws-psoxy-bulk-existing`](../aws-psoxy-bulk-existing).

For example usage, see: [modular-examples/aws-msft-365/main.tf](../../modular-examples/aws-msft-365/main.tf)
