# gcp-psoxy-cloud-function

Provisions a psoxy instance as a GCP Cloud Function.

As of Oct 2021, this cannot be done entirely via Terraform. This module will create a markdown file
named something like `TODO - deploy psoxy-function.md` which will provide you further instructions,
including building your psoxy instance from source if needed and executing `gcloud` commands.

NOTE: the target GCP project must have been provisioned to support Psoxy (eg, use `modules/gcp` once
for the project).


