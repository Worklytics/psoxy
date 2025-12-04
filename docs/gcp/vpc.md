
# VPC ***beta***

As of v0.5.6, GCP-hosted proxy instances are [Cloud Run Functions](https://cloud.google.com/run/docs/functions/comparison). This serverless architecture amounts to code being executed by GCP within a sandbox environment, with low-level networking fully-managed by Google.  This means that we don't support VPC-networking-level controls for ingress to the cloud functions; such connectivity/routing is managed by Google and secured by GCP IAM.  However, Cloud Run Functions do support routing all outbound traffic through a VPC using either [Direct VPC Egress](https://docs.cloud.google.com/run/docs/configuring/connecting-vpc) (recommended) or a [serverless VPC connector](https://cloud.google.com/vpc/docs/serverless-vpc-access), which provides some opportunity for limited egress connectivity and/or allowing proxy instances to connect to data sources that are otherwise on a private VPC that is not externally accessible (eg, a self-hosted JIRA instance).  Your VPC must provide connectivity to all data sources you wish to connect to; Google Workspace sources seem to work relying on the default "private Google access"; but all others likely require a Cloud Router + NAT; such networking configuration is outside the scope of what our terraform modules provide.

## Direct VPC Egress (Recommended)

By default, when you provide `network` and `subnet` in your `vpc_config`, the module will use **Direct VPC Egress**, which provides better performance, lower latency, and no additional VM charges compared to Serverless VPC Access connectors.

```hcl
vpc_config = {
  network = "my-vpc-network"
  subnet  = "my-vpc-subnet"
  network_tags = ["tag1", "tag2"]  # Optional: network tags for firewall rules
}
```

**Benefits of Direct VPC Egress:**
- Lower latency and higher throughput
- No additional VM charges (only network egress charges)
- Finer granularity with network tags per service
- No subnet size restrictions (unlike connectors which require /28)

## Serverless VPC Access Connector (Legacy)

If you have an existing Serverless VPC Access connector or prefer to use one, you can explicitly provide it:

```hcl
vpc_config = {
  serverless_connector = "projects/my-proxy-deployment/locations/us-central1/connectors/my-connector" # full resource ID of your Serverless VPC connector
}
```

**IMPORTANT:** When using Serverless VPC Access connectors, the subnet **must** have a `/28` netmask (e.g., `10.8.0.0/28`). This is a Google Cloud requirement for subnets used with VPC Serverless Connectors. If your existing subnet has a larger range (e.g., `/24` or `/16`), you will need to create a dedicated `/28` subnet for the VPC connector.

The following IAM roles, or equivalent subset of perms, may be required:
- `roles/compute.networkAdmin` - a read-only equivalent could be used if both your network and subnet exist
- `roles/vpcaccess.admin` - only required if using Serverless VPC Access connector

So this is connecting your proxy instances to and through a VPC, but they are otherwise not "on" the VPC. (Eg, will not run inside container instances with NICs bound to IPs on the VPC).

NOTE: Historically, there were GCP Cloud Functions; these are now called "GCP Cloud Run Functions (Gen1)"; we are using what would be the "GCP Cloud Run Functions (gen2)", which Google now brands simply as "Cloud Run Functions".

NOTE: **Direct VPC Egress is recommended** over Serverless VPC Access connectors as it provides better performance, lower latency, and no additional VM charges. The module uses Direct VPC Egress by default when `network` and `subnet` are provided.

NOTE: VPC Serverless Connectors are a potential bottleneck; please monitor to ensure sufficient capacity for your workload. If you need to use a connector, provision it externally and pass it in via `serverless_connector`.

NOTE: VPC resources are billable GCP resources; using them will increase your costs for hosting your proxy instances. With Direct VPC Egress, you only pay for network egress charges (no VM charges).

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

**Subnet Requirements:** When using Direct VPC Egress (default), there are no subnet size restrictions. However, if you're using a Serverless VPC Access connector (by providing `serverless_connector`), the subnet **must** have a `/28` netmask. If your subnet has a different netmask and you need to use a connector, you'll need to create a new subnet with `/28`.

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
    # Direct VPC Egress will be used by default (no serverless_connector needed)
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
