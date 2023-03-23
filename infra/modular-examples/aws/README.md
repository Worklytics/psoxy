# aws-google-workspace

Module for deploying Worklytics Psoxy on AWS.

See [infra/examples/aws](../../examples/aws) for example usage.

This is a root module grouping most common resources/modules used by orgs deploying Psoxy, so their
customization can be concisely done via a minimal terraform configuration that just invokes this
module plus bootstraps the AWS account/GCP project required.


