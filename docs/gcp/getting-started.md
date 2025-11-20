# Getting Started - GCP

## Overview

You'll provision infrastructure that ultimately looks as follows:

![GCP Archiecture Diagram.png](gcp-arch-diagram.jpg)

This includes:

- [Cloud Run Functions](https://cloud.google.com/run/docs) - serverless containerized applications
- [Service Accounts](https://cloud.google.com/iam/docs/service-accounts) - identity and access management
- [Secret Manager](https://cloud.google.com/secret-manager/docs) - to hold pseudonymization salt, encryption keys, and data source API keys
- [Cloud Storage Buckets](https://cloud.google.com/storage/docs) (GCS), if using Psoxy to sanitize bulk file data, such as CSVs; collecting data via webhooks; or async API requests.
- [Cloud KMS Keys](https://cloud.google.com/kms/docs) for webhook authentication (if using webhook collection mode).
- [Pub/Sub Topics and Subscriptions](https://cloud.google.com/pubsub/docs), for webhook message queuing and batch processing (if using webhook collectors); and async API requests (if using an API connector that supports/requires async mode).

NOTE: if you're connecting to Google Workspace as a data source, you'll also need to provision Service Account Keys and activate Google Workspace APIs. This may be located in same GCP project where you deploy the proxy, but that is not required; it can be managed by our provided Terraform modules - or you provision the services accounts/keys outside of terraform and fill them into the secrets manager on your own.

## Prerequisites

- a Google Project (we recommend a _dedicated_ GCP project for your deployment, to provide an implicit security boundary around your infrastructure as well as simplify monitoring/cleanup)
- some set of the IAM permissions and GCP services detailed below. The exact set required depends on the data sources you intend to use, and the [data processing mode(s)](https://docs.worklytics.co/psoxy/overview#modes) used for each.

### IAM Permissions
- a GCP (Google) user or Service Account with permissions to provision Service Accounts, Secrets, Storage Buckets, Cloud Run Functions, KMS Keys, Pub/Sub Topics/Subscriptions, and enable APIs within that project. eg:
  - [Cloud Functions Developer](https://docs.cloud.google.com/iam/docs/roles-permissions/cloudfunctions#cloudfunctions.developer) - proxy instances are deployed as GCP cloud functions
  - [Cloud KMS Admin](https://docs.cloud.google.com/iam/docs/roles-permissions/cloudkms#cloudkms.admin) - webhook authentication keys are provisioned as KMS asymmetric signing keys. this is only required for Webhook collection mode.
  - [Cloud Run Developer](https://docs.cloud.google.com/iam/docs/roles-permissions/run#run.developer) - cloud function deployment requires Cloud Run Developer role
  - [Cloud Scheduler Admin](https://cloud.google.com/iam/docs/roles-permissions/cloudscheduler#cloudscheduler.admin) - cloud scheduler - required if using Webhook Collector mode.
  - [Cloud Storage Admin](https://cloud.google.com/iam/docs/roles-permissions/storage#storage.admin) - processing of bulk data (such as HRIS exports) uses GCS buckets, as does Webhook Collection and async API request mode.
  - [IAM Role Admin](https://cloud.google.com/iam/docs/roles-permissions/iam#iam.roleAdmin) - create custom roles for the proxy, to follow principle of least privilege
  - [Project IAM Admin](https://cloud.google.com/iam/docs/roles-permissions/resourcemanager#resourcemanager.projectIamAdmin) - to bind IAM policies at project level. **Note:** If you prefer not to grant this role, you have two options: (1) grant it initially, then replace it with a read-only role after the initial apply, OR (2) set `provision_pubsub_publisher_to_gcs_default_service_account=false` in your Terraform configuration and grant the `roles/pubsub.publisher` role to the default GCS storage service account (format: `service-PROJECT_NUMBER@gs-project-accounts.iam.gserviceaccount.com`) outside of the provided Terraform modules.
  - [Secret Manager Admin](https://docs.cloud.google.com/iam/docs/roles-permissions/secretmanager#secretmanager.admin) - your API keys and pseudonymization salt is stored in Secret Manager
  - [Service Account Admin](https://docs.cloud.google.com/iam/docs/roles-permissions/iam#iam.serviceAccountAdmin) - admin Service Accounts that personify Cloud Functions or are used as Google Workspace API connections
  - [Service Usage Admin](https://docs.cloud.google.com/iam/docs/roles-permissions/serviceusage#serviceusage.serviceUsageAdmin) - you will need to enable various GCP APIs
  - [Pub/Sub Admin](https://docs.cloud.google.com/iam/docs/roles-permissions/pubsub#pubsub.admin) - webhook messages are queued in Pub/Sub topics and subscriptions for batch processing; also used for Async API mode requests.

NOTE: the above are the least-privileged predefined GCP roles; depending on your use-cases for the proxy, you can likely create a less-privileged [custom GCP IAM role](https://cloud.google.com/iam/docs/creating-custom-roles) that will suffice. 

### APIS
- the following APIs enabled in the project: (via [GCP Console](https://console.cloud.google.com/projectselector2/apis/dashboard))
  - [IAM Service Account Credentials API](https://console.cloud.google.com/apis/library/iamcredentials.googleapis.com) (`iamcredentials.googleapis.com`) - generally needed to support authenticating Terraform. May not be needed if you're running `terraform` within a GCP environment.
  - [Service Usage API](https://console.cloud.google.com/apis/library/serviceusage.googleapis.com) (`serviceusage.googleapis.com`)
- additional APIs enabled in the project: (using the `Service Usage API` above, our Terraform will _attempt_ to enable these, but as there is sometimes a few minutes delay in activation and in some cases they are required to read your existing infra prior to apply, you may experience errors. To pre-empt those, we suggest ensuring the following are enabled:
  - [Artifact Registry API](https://console.cloud.google.com/apis/library/artifactregistry.googleapis.com) (`artifactregistry.googleapis.com`)
  - [Cloud Build API](https://console.cloud.google.com/apis/library/cloudbuild.googleapis.com) (`cloudbuild.googleapis.com`)
  - [Cloud Functions API](https://console.cloud.google.com/apis/library/cloudfunctions.googleapis.com) (`cloudfunctions.googleapis.com`)
  - [Cloud Resource Manager API](https://console.cloud.google.com/apis/library/cloudresourcemanager.googleapis.com) (`cloudresourcemanager.googleapis.com`)
  - [Compute Engine API](https://console.cloud.google.com/apis/library/compute.googleapis.com) (`compute.googleapis.com`)
  - [Eventarc API](https://console.cloud.google.com/apis/library/eventarc.googleapis.com) (`eventarc.googleapis.com`)
  - [IAM API](https://console.cloud.google.com/apis/library/iam.googleapis.com) (`iam.googleapis.com`)
  - [Pub/Sub API](https://console.cloud.google.com/apis/library/pubsub.googleapis.com) (`pubsub.googleapis.com`)
  - [Secret Manager API](https://console.cloud.google.com/apis/library/secretmanager.googleapis.com) (`secretmanager.googleapis.com`)
  - [Storage API](https://console.cloud.google.com/apis/library/storage-api.googleapis.com) (`storage-api.googleapis.com`)
  - [VPC Accesss API](https://console.cloud.google/com/apis/library/vpcaccess.googleapis.com) (`vpcaccess.googleapis.com`), if relying on our provisioning a Serverless VPC Connector

### Terraform State Backend

You'll also need a secure backend location for your Terraform state (such as a GCS or S3 bucket). It need not be in the same host platform/project/account to which you are deploying the proxy, as long as the Google/AWS user you are authenticated as when running Terraform has permissions to access it.

Some options:

- [GCS](https://developer.hashicorp.com/terraform/language/settings/backends/gcs)
- [S3](https://developer.hashicorp.com/terraform/language/settings/backends/s3)

Alternatively, you may use a local file system, but this is not recommended for production use - as your Terraform state may contain secrets such as API keys, depending on the sources you connect.

See: [https://developer.hashicorp.com/terraform/language/settings/backends/local](https://developer.hashicorp.com/terraform/language/settings/backends/local)

## Bootstrap

For some help in bootstraping a GCP environment, see also: [infra/modules/gcp-bootstrap/README.md](../../infra/modules/gcp-bootstrap/README.md)

The module [psoxy-constants](../../infra/modules/psoxy-constants) is a dependency-free module that provides lists of GCP roles, etc needed for bootstraping a GCP project in which your proxy instances will reside.

## Example

The [Worklytics/psoxy-example-gcp](https://github.com/Worklytics/psoxy-example-gcp) repo provides an example configuration for hosting proxy instances in GCP. You use that template, following its `Usage` docs to get started.

## Security Considerations

- the 'Service Account' approach described in the prerequisites is preferable to giving a Google user account IAM roles to administer your infrastructure directly. You can pass this Service Account's email address to Terraform by setting the `gcp_terraform_sa_account_email`. Your machine/environments CLI must be authenticated as GCP entity which can impersonate this Service Account, and likely create tokens as it (`Service Account Token Creator` role).
- using a _dedicated_ GCP project is superior to using a shared project, as it provides an implicit security boundary around your infrastructure as well as simplifying monitoring/cleanup. The IAM roles specified in the prerequisites must be granted at the project level, so any non-Proxy infrastructure within the GCP project that hosts your proxy instances will be accessible to the user / service account who's managing the proxy infrastructure.
- fixed IPS for your outbound API requests to data sources. This requires [configuring VPC and some additional features](https://docs.worklytics.co/psoxy/gcp/vpc). This is beyond scope of Worklytics support, but our provided terraform configurations and proxy code are compatible with using such features.