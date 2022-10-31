# aws-google-workspace

Module for deploying Worklytics Psoxy on AWS for Google Workspace-based organization.

See [infra/examples/aws-google-workspace](../../examples/aws-google-workspace) for example usage.

This is a root module grouping most common resources/modules used by orgs deploying Psoxy, so their
customization can be concisely done via a minimal terraform configuration that just invokes this
module plus bootstraps the AWS account/GCP project required.


