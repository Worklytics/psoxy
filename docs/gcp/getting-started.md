# Getting Started - GCP

## Prerequisites

A Google (GCP) user who can create projects (or with sufficient permissions to provision Service 
Accounts, Keys, Secret Manager, GCS buckets, and Cloud Functions within an existing project).


## Terraform State Backend

You'll also need a backend location for your Terraform state (such as an S3 bucket). It can be in
any AWS account, as long as the AWS role that you'll use to run Terraform has read/write access to
it.

Alternatively, you may use a local file system, but this is not recommended for production use - as
your Terraform state may contain secrets such as API keys, depending on the sources you connect.

See also: infra/modules/gcp-bootstrap/README.md
