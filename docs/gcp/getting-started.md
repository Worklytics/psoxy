# Getting Started - GCP

## Overview

You'll provision infrastructure that ultimately looks as follows:

![GCP Archiecture Diagram.png](gcp-arch-diagram.jpg)

This includes:

- [Cloud Run Functions](https://cloud.google.com/run/docs) - serverless containerized applications
- [Service Accounts](https://cloud.google.com/iam/docs/service-accounts) - identity and access management
- [Secret Manager](https://cloud.google.com/secret-manager/docs) - to hold pseudonymization salt, encryption keys, and data source API keys
- [Cloud Storage Buckets](https://cloud.google.com/storage/docs) (GCS), if using psoxy to sanitize bulk file data, such as CSVs; or collecting data via webhooks
- [Cloud KMS Keys](https://cloud.google.com/kms/docs) for webhook authentication (if using webhook collectors)
- [Pub/Sub Topics and Subscriptions](https://cloud.google.com/pubsub/docs), for webhook message queuing and batch processing (if using webhook collectors)

NOTE: if you're connecting to Google Workspace as a data source, you'll also need to provision Service Account Keys and activate Google Workspace APIs.

## Prerequisites

- a Google Project (we recommend a _dedicated_ GCP project for your deployment, to provide an implicit security boundary around your infrastructure as well as simplify monitoring/cleanup)
- a GCP (Google) user or Service Account with permissions to provision Service Accounts, Secrets, Storage Buckets, Cloud Run Functions, KMS Keys, Pub/Sub Topics/Subscriptions, and enable APIs within that project. eg:
  - [Cloud Functions Developer](https://cloud.google.com/iam/docs/understanding-roles#cloudfunctions.developer) - proxy instances are deployed as GCP cloud functions
  - [Cloud Run Developer](https://cloud.google.com/iam/docs/understanding-roles#cloudrun.developer) - cloud function deployment requires Cloud Run Developer role
  - [Cloud Storage Admin](https://cloud.google.com/iam/docs/understanding-roles#storage.admin) - processing of bulk data (such as HRIS exports) uses GCS buckets
  - [IAM Role Admin](https://cloud.google.com/iam/docs/understanding-roles#iam.roles.admin) - create custom roles for the proxy, to follow principle of least privilege
  - [Secret Manager Admin](https://cloud.google.com/iam/docs/understanding-roles#secretmanager.admin) - your API keys and pseudonymization salt is stored in Secret Manager
  - [Service Account Admin](https://cloud.google.com/iam/docs/understanding-roles#iam.serviceAccountAdmin) - admin Service Accounts that personify Cloud Functions or are used as Google Workspace API connections
  - [Service Usage Admin](https://cloud.google.com/iam/docs/understanding-roles#serviceusage.serviceUsageAdmin) - you will need to enable various GCP APIs
  - [Cloud KMS Admin](https://cloud.google.com/iam/docs/understanding-roles#cloudkms.admin) - webhook authentication keys are provisioned as KMS asymmetric signing keys
  - [Pub/Sub Admin](https://cloud.google.com/iam/docs/understanding-roles#pubsub.admin) - webhook messages are queued in Pub/Sub topics and subscriptions for batch processing
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

- GCS : https://developer.hashicorp.com/terraform/language/settings/backends/gcs
- S3 : https://developer.hashicorp.com/terraform/language/settings/backends/s3

Alternatively, you may use a local file system, but this is not recommended for production use - as your Terraform state may contain secrets such as API keys, depending on the sources you connect.

See: https://developer.hashicorp.com/terraform/language/settings/backends/local

## Bootstrap

For some help in bootstraping a GCP environment, see also: [infra/modules/gcp-bootstrap/README.md](../../infra/modules/gcp-bootstrap/README.md)

The module [psoxy-constants](../../infra/modules/psoxy-constants) is a dependency-free module that provides lists of GCP roles, etc needed for bootstraping a GCP project in which your proxy instances will reside.

## Example

The [Worklytics/psoxy-example-gcp](https://github.com/Worklytics/psoxy-example-gcp) repo provides an example configuration for hosting proxy instances in GCP. You use that template, following its `Usage` docs to get started.

## Security Considerations

- the 'Service Account' approach described in the prerequisites is preferable to giving a Google user account IAM roles to administer your infrastructure directly. You can pass this Service Account's email address to Terraform by setting the `gcp_terraform_sa_account_email`. Your machine/environments CLI must be authenticated as GCP entity which can impersonate this Service Account, and likely create tokens as it (`Service Account Token Creator` role).
- using a _dedicated_ GCP project is superior to using a shared project, as it provides an implicit security boundary around your infrastructure as well as simplifying monitoring/cleanup. The IAM roles specified in the prerequisites must be granted at the project level, so any non-Proxy infrastructure within the GCP project that hosts your proxy instances will be accessible to the user / service account who's managing the proxy infrastructure.

## VPC **alpha**

As of v0.5.6, GCP-hosted proxy instances are [Cloud Run Functions](https://cloud.google.com/run/docs/functions/comparison). This serverless architecture amounts to code being executed by GCP within a sandbox environment, withs low-level networking fully-managed by Google, for better or worse.  This means that we don't support VPC-networking-level controls for ingress to the cloud functions; such connectivity/routing is managed by Google and secured by GCP IAM.  However, Cloud Run Functions do support routing all outbound traffic through a [serverless VPC connector](https://cloud.google.com/vpc/docs/serverless-vpc-access), which provides some opportunity for limited egress connectivity and/or allowing proxy instances to connect to data sources that are otherwise on a private VPC that is not externally accessible (eg, a self-hosted JIRA instance).

To configure a VPC / serverless VPC connector, add the following to your `terraform.tfvars`:

```hcl
vpc_config = {
  network = "my-vpc" # provide if you want our terraform to provision serverless connector for you; ignored if 'serverless_connector' provided
  serverless_connector = "projects/my-proxy-deployment/locations/us-central1/connectors/my-connector" # full resource ID of your Serverless VPC connector
  serverless_connector_cidr_range= "10.8.0.0/24" # this is the default; omit to use it; ignored if 'serverless_connector' provided
}
```

So this is connecting your proxy instances to and through a VPC, but they are otherwise not "on" the VPC. (Eg, will not run inside container instances with NICs bound to IPs on the VPC).

NOTE: Historically, there were GCP Cloud Functions; these are now called "GCP Cloud Run Functions (Gen1)"; we are using what would be the "GCP Cloud Run Functions (gen2)", which Google now brands simply as "Cloud Run Functions".

NOTE: VPC Serverless Connectors, whether managed via our provided Terraform or not, are a potential bottleneck; please monitor to ensure sufficient capacity for your workload. If the one we
provision is not sufficient for your use-case, please provision it externally and pass it in. 

NOTE: VPC resources, including serverless connectors, are billable GCP resources; provisioning and using them will increase your costs for hosting your proxy instances.


### Min Network Example

```hcl
locals {
  environment_id = "proxy_example_"
}

resource "google_compute_network" "vpc" {
  project                 = "my-gcp-project"
  name                    = "${local.environment_id}vpc"
  auto_create_subnetworks = false
}

resource "google_compute_subnetwork" "subnet" {
  project                  = "my-gcp-project"
  network                  = google_compute_network.vpc.id
  region                   = var.gcp_region
  name                     = "${local.environment_id}subnet"
  ip_cidr_range            = "10.6.0.0/24"
  private_ip_google_access = true
}
```


### Fixed IP Out
To have your proxy instances "dial out" from a fixed IP, you must do the above as well set-up a router + NAT + IP on your network; example of which follows.

```hcl
# Static egress IP via Cloud NAT
local {
  fixed_egress_ip=true
}

resource "google_compute_address" "fixed_egress_ip" {
  count = local.fixed_egress_ip ? 1 : 0

  name   = "${local.environment_id_prefix}nat-egress-ip"
  region = var.gcp_region
}

resource "google_compute_router" "router" {
  count = local.provision_vpc ? 1 : 0

  name    = "${local.environment_id_prefix}router"
  region  = var.gcp_region
  network = google_compute_network.vpc[0].name
}

resource "google_compute_router_nat" "nat" {
  count = local.provision_vpc ? 1 : 0

  name   = "${local.environment_id_prefix}nat"
  router = google_compute_router.router[0].name
  region = var.gcp_region

  nat_ip_allocate_option = local.fixed_egress_ip ? "MANUAL_ONLY" : "AUTO_ONLY"
  nat_ips                = local.fixed_egress_ip ? [google_compute_address.fixed_egress_ip[0].self_link] : []

  source_subnetwork_ip_ranges_to_nat = "ALL_SUBNETWORKS_ALL_IP_RANGES"
}
```

This would in principal allow you to limit data source access by IP. Note that the NAT and router above may present scalability issues.