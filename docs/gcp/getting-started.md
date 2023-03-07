# Getting Started - GCP

## Overview

You'll provision infrastructure that ultimately looks as follows:

![GCP Archiecture Diagram.png](gcp-arch-diagram.jpg)

This includes:
  - Cloud Functions
  - Service Accounts
  - Secret Manager Secrets, to hold pseudonymization salt, encryption keys, and data source API keys
  - Cloud Storage Buckets (GCS), if using psoxy to sanitize bulk file data, such as CSVs

NOTE: if you're connecting to Google Workspace as a data source, you'll also need to provision
Service Account Keys and activate Google Workspace APIs.

## Prerequisites

  - a Google Project (or permissions to create one)
  - permissions to create Service Accounts, Secret Manager Secrets, Cloud Storage Buckets, and Cloud
    Functions within that project; eg:
     * [Service Account Creator](https://cloud.google.com/iam/docs/understanding-roles#iam.serviceAccountCreator) - create Service Accounts to personify Cloud Functions
     * [Cloud Functions Admin](https://cloud.google.com/iam/docs/understanding-roles#cloudfunctions.admin) - proxy instances are deployed as GCP cloud functions
     * [Cloud Storage Admin](https://cloud.google.com/iam/docs/understanding-roles#storage.admin) - processing of bulk data (such as HRIS exports) uses GCS buckets
     * [Secret Manager Admin](https://cloud.google.com/iam/docs/understanding-roles#secretmanager.admin) - your API keys and pseudonymization salt is stored in Secret Manager
     * [Service Usage Admin](https://cloud.google.com/iam/docs/understanding-roles#serviceusage.serviceUsageAdmin) - you will need to enable various GCP APIs

### Terraform State Backend

You'll also need a secure backend location for your Terraform state (such as a GCS or S3 bucket). It
need not be in the same host platform/project/account to which you are deploying the proxy, as long
as the Google/AWS user you are authenticated as when running Terraform has permissions to access it.

Alternatively, you may use a local file system, but this is not recommended for production use - as
your Terraform state may contain secrets such as API keys, depending on the sources you connect.

See also: [infra/modules/gcp-bootstrap/README.md](../../infra/modules/gcp-bootstrap/README.md)
