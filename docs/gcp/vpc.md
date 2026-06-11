
# VPC ***beta***

As of v0.5.6, GCP-hosted proxy instances are [Cloud Run Functions](https://cloud.google.com/run/docs/functions/comparison). This serverless architecture amounts to code being executed by GCP within a sandbox environment, with low-level networking fully-managed by Google.  This means that we don't support VPC-networking-level controls for ingress to the cloud functions; such connectivity/routing is managed by Google and secured by GCP IAM.  However, Cloud Run Functions do support routing all outbound traffic through a [serverless VPC connector](https://cloud.google.com/vpc/docs/serverless-vpc-access), which provides some opportunity for limited egress connectivity and/or allowing proxy instances to connect to data sources that are otherwise on a private VPC that is not externally accessible (eg, a self-hosted JIRA instance).  Your VPC must provide connectivity to all data sources you wish to connect to; Google Workspace sources seem to work relying on the default "private Google access"; but all others likely require a Cloud Router + NAT; such networking configuration is outside the scope of what our terraform modules provide.

To configure a VPC / serverless VPC connector, provide exactly ONE of the following to the `vpc_config` in your `terraform.tfvars`: 1) a `network`, 2) a `subnet`, or 3) `serverless_connector`; if you provide just the `network`, the latter two will be provisioned for you; if you provide just the `subnet`, the connector will be provisioned for you.

**IMPORTANT:** When using the `subnet` approach, the subnet **must** have a `/28` netmask (e.g., `10.8.0.0/28`). This is a Google Cloud requirement for subnets used with VPC Serverless Connectors. If your existing subnet has a larger range (e.g., `/24` or `/16`), you will need to create a dedicated `/28` subnet for the VPC connector.

```hcl
vpc_config = {
  serverless_connector = "projects/my-proxy-deployment/locations/us-central1/connectors/my-connector" # full resource ID of your Serverless VPC connector
}
```

The following IAM roles, or equivalent subset of perms, may be required:
- `roles/compute.networkAdmin` - a read-only equivalent could be used if both your network and subnet exist
- `roles/vpcaccess.admin` - a read-only equivalent could be used if serverless VPC connector exists

So this is connecting your proxy instances to and through a VPC, but they are otherwise not "on" the VPC. (Eg, will not run inside container instances with NICs bound to IPs on the VPC).

NOTE: Historically, there were GCP Cloud Functions; these are now called "GCP Cloud Run Functions (Gen1)"; we are using what would be the "GCP Cloud Run Functions (gen2)", which Google now brands simply as "Cloud Run Functions".

NOTE: VPC Serverless Connectors, whether managed via our provided Terraform or not, are a potential bottleneck; please monitor to ensure sufficient capacity for your workload. If the one we provision is not sufficient for your use-case, please provision it externally and pass it in.

NOTE: VPC resources, including serverless connectors, are billable GCP resources; provisioning and using them will increase your costs for hosting your proxy instances.

## Min Network Example

```hcl
locals {
  environment_id = "proxy_example_"
}

resource "google_compute_network" "vpc" {
  project                 = "my-gcp-project"
  name                    = "${local.environment_id}vpc"
  auto_create_subnetworks = true
}

resource "google_compute_network" "vpc_network" {
    project                 = "my-gcp-project"
    name                    = "${local.environment_id}vpc"
    auto_create_subnetworks = false
    mtu                     = 1460
}

# NOTE: Subnet must have /28 netmask (required by Google Cloud for VPC connectors)
resource "google_compute_subnetwork" "default" {
    name          = "my-custom-subnet"
    ip_cidr_range = "10.0.1.0/28"
    region        = var.gcp_region
    network       = google_compute_network.vpc_network.id
}

```

Then add the following specifically to the psoxy module in our Terraform example, e.g.:

```hcl
module "psoxy" {
  source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-host?ref=v0.5.11"

  ...
  vpc_config = {
    network = google_compute_network.vpc.name
    subnet = google_compute_subnetwork.default.name
  }
   ...
}
```

NOTE: use of `name`, rather than `id` as the attribute values.

Alternatively, if you created the VPC *outside* of the terraform configuration in which you're managing psoxy, you can provide it in your `terraform.tfvars`:

```hcl
vpc_config = {
  network = "proxy_example_vpc"
  subnet = "proxy_example_subnet"  # NOTE: Subnet must have /28 netmask (required by Google Cloud for VPC connectors)
}
```

**Subnet Requirements:** When specifying a `subnet`, ensure it has a `/28` netmask. Google Cloud requires this specific netmask for subnets used with VPC Serverless Connectors. If your subnet has a different netmask, you'll need to create a new subnet with `/28`.

## Shared VPC connector

Same as above, but with a VPC hosted on a different project. This setup is useful when you want to share a VPC between multiple projects
and have a centralized network configuration.

Preconditions:

- `HOST_PROJECT` - the project that hosts the network/subnetwork to be shared
- `SERVICE_PROJECT` - the project that hosts the cloud run function
- on host project, grant the role `roles/compute.networkUser` or equivalent to the following service accounts:
  - `service-SERVICE_PROJECT@serverless-robot-prod.iam.gserviceaccount.com`
  - `service-SERVICE_PROJECT@gcp-sa-vpcaccess.iam.gserviceaccount.com`
- Remember: the subnetwork **MUST BE** a `/28` netmask (required by Google Cloud for VPC connectors)

Fill the `vpc_config` in your `terraform.tfvars`, as follows, providing the full **self_links** to network and subnetwork on the HOST PROJECT:

```hcl
vpc_config = {
    network = "projects/HOST_PROJECT/global/networks/NAME"
    subnet = "projects/HOST_PROJECT/regions/REGION/subnetworks/SUBNETWORK_NAME"
    serverless_connector = null
}
```

## Fixed IP Out
To have your proxy instances "dial out" from a fixed IP, you must do the above as well set-up a router + NAT + IP on your network; example of which follows.

```hcl
# Static egress IP via Cloud NAT
locals {
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
