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

  - a Google Project
      - we recommend a *dedicated* GCP project for your deployment, to provide an implicit security
        boundary around your infrastructure as well as simplify monitoring/cleanup
  - the following APIs enabled in the project: (via [GCP Console](https://console.cloud.google.com/projectselector2/apis/dashboard))
      - [Service Usage API](https://console.cloud.google.com/apis/library/serviceusage.googleapis.com) (`serviceusage.googleapis.com`)
  - a GCP (Google) user or Service Account with permissions to create Service Accounts, Secrets,
    Storage Buckets, Cloud Functions, and enable APIs within that project. eg:
     * [Service Account Creator](https://cloud.google.com/iam/docs/understanding-roles#iam.serviceAccountCreator) - create Service Accounts to personify Cloud Functions (aka, 'Create Service Accounts' in GCP console UX)
     * [Cloud Functions Admin](https://cloud.google.com/iam/docs/understanding-roles#cloudfunctions.admin) - proxy instances are deployed as GCP cloud functions
     * [Cloud Storage Admin](https://cloud.google.com/iam/docs/understanding-roles#storage.admin) - processing of bulk data (such as HRIS exports) uses GCS buckets
     * [Secret Manager Admin](https://cloud.google.com/iam/docs/understanding-roles#secretmanager.admin) - your API keys and pseudonymization salt is stored in Secret Manager
     * [Service Usage Admin](https://cloud.google.com/iam/docs/understanding-roles#serviceusage.serviceUsageAdmin) - you will need to enable various GCP APIs

### Terraform State Backend

You'll also need a secure backend location for your Terraform state (such as a GCS or S3 bucket). It
need not be in the same host platform/project/account to which you are deploying the proxy, as long
as the Google/AWS user you are authenticated as when running Terraform has permissions to access it.

Some options:
  - GCS : https://developer.hashicorp.com/terraform/language/settings/backends/gcs
  - S3 : https://developer.hashicorp.com/terraform/language/settings/backends/s3

Alternatively, you may use a local file system, but this is not recommended for production use - as
your Terraform state may contain secrets such as API keys, depending on the sources you connect.

See: https://developer.hashicorp.com/terraform/language/settings/backends/local

## Bootstrap

For some help in bootstraping a GCP environment, see also: [infra/modules/gcp-bootstrap/README.md](../../infra/modules/gcp-bootstrap/README.md)

