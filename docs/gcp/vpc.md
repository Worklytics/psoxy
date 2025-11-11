
# VPC ***beta***

As of v0.5.6, GCP-hosted proxy instances are [Cloud Run Functions](https://cloud.google.com/run/docs/functions/comparison). This serverless architecture amounts to code being executed by GCP within a sandbox environment, with low-level networking fully-managed by Google.  This means that we don't support VPC-networking-level controls for ingress to the cloud functions; such connectivity/routing is managed by Google and secured by GCP IAM.  However, Cloud Run Functions do support routing all outbound traffic through a [serverless VPC connector](https://cloud.google.com/vpc/docs/serverless-vpc-access), which provides some opportunity for limited egress connectivity and/or allowing proxy instances to connect to data sources that are otherwise on a private VPC that is not externally accessible (eg, a self-hosted JIRA instance).  Your VPC must provide connectivity to all data sources you wish to connect to; Google Workspace sources seem to work relying on the default "private Google access"; but all others likely require a Cloud Router + NAT; such networking configuration is outside the scope of what our terraform modules provide.

To configure a VPC / serverless VPC connector, provide exactly ONE of the following to the `vpc_config` in your `terraform.tfvars`: 1) a `network`, 2) a `subnetwork`, or 3) `serverless_connector`; if you provide just the `network`, the latter two will be provisioned for you; if you provide just the `subnetwork`, the connector will be provisioned for you.

```hcl
vpc_config = {
  serverless_connector = "projects/my-proxy-deployment/locations/us-central1/connectors/my-connector" # full resource ID of your Serverless VPC connector
}
```

The following IAM roles, or equivalent subset of perms, may be required:
- `roles/compute.networkAdmin` - a read-only equivalent could be used if both your network and subnetwork exist
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

```

Then add the following specifically to the psoxy module in our Terraform example, e.g.:

```hcl
module "psoxy" {
  source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-host?ref=v0.5.11"

  ...
  vpc_config = {
    network = google_compute_network.vpc.name
  }
   ...
}
```

NOTE: use of `name`, rather than `id` as the attribute values.

Alternatively, if you created the VPC *outside* of the terraform configuration in which you're managing psoxy, you can provide it in your `terraform.tfvars`:

```hcl
vpc_config = {
  network = "proxy_example_vpc"
}
```

or

```hcl
vpc_config = {
  subnetwork = "proxy_example_subnet"
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