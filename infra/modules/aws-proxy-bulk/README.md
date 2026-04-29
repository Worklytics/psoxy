# aws-psoxy-bulk

This module provisions an AWS Lambda that sanitizes files uploaded into an input bucket, and writes the sanitized files to an output bucket.

This module provisions the Lambda + both buckets. If you have an existing input bucket you want to
use, see [`../aws-psoxy-bulk-existing`](../aws-psoxy-bulk-existing).

For example usage, see: [modular-examples/aws-msft-365/main.tf](../../modular-examples/aws-msft-365/main.tf)
