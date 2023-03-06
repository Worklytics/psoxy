# Getting Started - GCP

## Prerequisites

A Google (GCP) user who can create projects (or with sufficient permissions to provision Service
Accounts, Secrets, GCS buckets, and Cloud Functions within an existing project).

 * [Service Account Creator](https://cloud.google.com/iam/docs/understanding-roles#iam.serviceAccountCreator) - create Service Accounts to personify Cloud Functions
 * [Cloud Functions Admin](https://cloud.google.com/iam/docs/understanding-roles#cloudfunctions.admin) - proxy instances are deployed as GCP cloud functions
 * [Cloud Storage Admin](https://cloud.google.com/iam/docs/understanding-roles#storage.admin) - processing of bulk data (such as HRIS exports) uses GCS buckets
 * [Secret Manager Admin](https://cloud.google.com/iam/docs/understanding-roles#secretmanager.admin) - your API keys and pseudonymization salt is stored in Secret Manager
 * [Service Usage Admin](https://cloud.google.com/iam/docs/understanding-roles#serviceusage.serviceUsageAdmin) - you will need to enable various GCP APIs

## Terraform State Backend

You'll also need a backend location for your Terraform state (such as an S3 bucket). It can be in
any AWS account, as long as the AWS role that you'll use to run Terraform has read/write access to
it.

Alternatively, you may use a local file system, but this is not recommended for production use - as
your Terraform state may contain secrets such as API keys, depending on the sources you connect.

See also: [infra/modules/gcp-bootstrap/README.md](../../infra/modules/gcp-bootstrap/README.md)
