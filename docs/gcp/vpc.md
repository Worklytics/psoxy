
# VPC ***beta***

As of v0.5.6, GCP-hosted proxy instances are [Cloud Run Functions](https://cloud.google.com/run/docs/functions/comparison) (gen2).

For **egress**, Psoxy supports [Direct VPC egress](https://cloud.google.com/functions/docs/running/direct-vpc): Cloud Functions attach a network interface directly to your VPC subnet and route outbound traffic through it. This does **not** require a Serverless VPC Access connector.

This document covers **egress** only. For optional **ingress** via an external Application Load Balancer (ALB) + Cloud Armor (beta; set `external_api_alb` on `gcp-host`), see [GCP External Application Load Balancer (ALB) + Cloud Armor](../development/gcp-external-alb.md). VPC-level ingress controls are not provided by these modules; default ingress remains Google-managed (`*.run.app`) with GCP IAM.

The legacy `serverless_connector` path remains supported but is deprecated (TODO: remove in 0.7.x).

The primary use case is **fixed egress IP**: route all outbound API traffic through your VPC, then through Cloud NAT with a reserved static IP, so data sources can allowlist a single address.

## Configuration

Provide `network` and `subnet` in `vpc_config` in your `terraform.tfvars` (or pass them into the `gcp-host` module). Use resource **names** when the VPC lives in the same project, or full **self-links** for Shared VPC.

```hcl
vpc_config = {
  network = "my-vpc"
  subnet  = "my-subnet"  # must be in the same region as your Cloud Functions
}
```

NOTE: use of `name`, rather than `id`, as the attribute values when referencing Terraform-managed resources.

The following IAM roles, or an equivalent subset of permissions, may be required on the **service project** (where Cloud Functions run):
- `roles/compute.networkAdmin` — if you create the VPC, subnet, router, or NAT in Terraform

On the **host project** (Shared VPC only), grant `roles/compute.networkUser` on the subnet to:
- `service-SERVICE_PROJECT_NUMBER@serverless-robot-prod.iam.gserviceaccount.com`

See [Google's Direct VPC IAM guidance](https://cloud.google.com/functions/docs/running/direct-vpc#set_up_iam_permissions).

NOTE: VPC networking resources (subnets, Cloud NAT, static IPs) are billable; using them will increase hosting costs.

Direct VPC egress requires Terraform Google provider **>= 7.21** (the `serverless_connector` path works with earlier provider versions). Pin that version in your root module's `required_providers`, and run `terraform init -upgrade` if your lockfile still resolves an older provider.

## Min Network Example

```hcl
locals {
  environment_id = "proxy_example_"
}

resource "google_compute_network" "vpc_network" {
  project                 = "my-gcp-project"
  name                    = "${local.environment_id}vpc"
  auto_create_subnetworks = false
  mtu                     = 1460
}

resource "google_compute_subnetwork" "default" {
  name          = "my-custom-subnet"
  ip_cidr_range = "10.0.1.0/24"
  region        = var.gcp_region
  network       = google_compute_network.vpc_network.id
}
```

Then add the following to the psoxy module in your Terraform configuration:

```hcl
module "psoxy" {
  source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp-host?ref=v0.5.11"

  ...
  vpc_config = {
    network = google_compute_network.vpc_network.name
    subnet  = google_compute_subnetwork.default.name
  }
  ...
}
```

Alternatively, if the VPC was created outside the psoxy Terraform configuration:

```hcl
vpc_config = {
  network = "proxy_example_vpc"
  subnet  = "proxy_example_subnet"
}
```

## Shared VPC

Use this when the VPC is hosted in a different project than the Cloud Functions.

Preconditions:

- `HOST_PROJECT` — project that hosts the shared network/subnetwork
- `SERVICE_PROJECT` — project that hosts the Cloud Functions
- On the host project, grant `roles/compute.networkUser` on the subnet to `service-SERVICE_PROJECT_NUMBER@serverless-robot-prod.iam.gserviceaccount.com`

Fill `vpc_config` with the full **self-links** to the network and subnetwork on the host project:

```hcl
vpc_config = {
  network = "projects/HOST_PROJECT/global/networks/NAME"
  subnet  = "projects/HOST_PROJECT/regions/REGION/subnetworks/SUBNETWORK_NAME"
}
```

## Fixed Egress IP

To have proxy instances dial out from a fixed IP, configure Direct VPC egress (above) **and** add Cloud Router + NAT with a reserved static IP on the same VPC. Example:

```hcl
locals {
  fixed_egress_ip = true
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

This allows data sources to restrict access by IP. The NAT and router above may present scalability limits at high volume; size them for your workload.

Your VPC must provide connectivity to all data sources you connect to. Google Workspace sources generally work with Private Google Access on the subnet; other SaaS APIs require the NAT path above to reach the public internet with your fixed IP.

## Removing VPC egress

GCP Cloud Functions (gen2) cannot clear Direct VPC egress or Serverless VPC Access connector settings via an in-place Terraform update. If you remove or comment out `vpc_config` and run `terraform apply`, you may see:

```
Error 400: Could not update Cloud Run service ... spec.template.metadata.annotations: The run.googleapis.com/vpc-access-egress annotation cannot be set without also setting the run.googleapis.com/vpc-access-connector annotation or the run.googleapis.com/network-interfaces annotation.
```

Workaround: destroy each affected Cloud Function in Terraform, then apply to recreate it without VPC networking.

1. Remove `vpc_config` from your `terraform.tfvars` (or set it to `null`).
2. List function resources in state:

```bash
terraform state list | grep google_cloudfunctions2_function
```

3. Destroy each function that previously had VPC egress (API connectors, webhook collectors, and bulk connectors if applicable). The resource address depends on your root module; with the standard `gcp-host` example it looks like:

```bash
# API connector (replace CONNECTOR_ID with the map key from your config, e.g. gcal, msft-teams)
terraform destroy -target='module.psoxy.module.api_connector["CONNECTOR_ID"].google_cloudfunctions2_function.function'

# Webhook collector
terraform destroy -target='module.psoxy.module.webhook_collector["WEBHOOK_ID"].google_cloudfunctions2_function.function'

# Bulk connector (only if it used a serverless connector)
terraform destroy -target='module.psoxy.module.bulk_connector["BULK_ID"].google_cloudfunctions2_function.function'
```

4. Recreate the functions:

```bash
terraform apply
```

Each destroyed function is recreated on apply. Expect brief downtime per connector while the function is destroyed and redeployed. IAM bindings and secrets are unchanged; only the Cloud Function resource is replaced.

See also [GCP troubleshooting](./troubleshooting.md#error-400-vpc-access-egress-annotation-cannot-be-set-without-connector-or-network-interfaces) for the same error with additional context.
