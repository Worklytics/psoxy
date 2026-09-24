# External Application Load Balancer (ALB)

> **Status: Beta.** Provisioned by `gcp-host` when `external_api_alb` is set, or bring-your-own via `api_connector_external_lb_host`. Interfaces may change in a future release.

Use this when Worklytics must reach API connectors over the public internet through a global external Application Load Balancer, optionally restricted to known source IPs with Cloud Armor. This is not Private Service Connect. Leave `external_api_alb` unset to keep the default Cloud Functions / Cloud Run URLs (`*.run.app`).

Implementation detail and troubleshooting live in [GCP External Application Load Balancer (ALB) + Cloud Armor](../../development/gcp-external-alb.md).

## Enable it

In `terraform.tfvars`:

```hcl
# Self-signed certificate on a reserved global IP (proof of concept)
external_api_alb = {}

# Or Google-managed TLS. Create the DNS record from the apply TODO before testing.
# external_api_alb = {
#   domain = "proxy.example.com"
# }
```

`terraform apply` then:

- Sets each API connector’s Cloud Functions ingress to `ALLOW_INTERNAL_AND_GCLB`, so internet clients reach the connector through the load balancer rather than `*.run.app`.
- Sets each connector’s public endpoint URL to `https://<host>/<function-name>/`. Test TODOs, generated test scripts, and the Worklytics "Psoxy Base URL" use that URL.
- For managed TLS (`domain` set), writes a DNS-setup TODO. Point that hostname at the reserved IP before you test or connect Worklytics.

Optionally set `allowed_data_access_ip_blocks` to the Worklytics egress CIDRs. A non-null list attaches Cloud Armor allow/deny rules on the load balancer as well as the application-layer check. Leave it `null` for open ingress (IAM and application auth still apply). An empty list is invalid.

If you already operate the load balancer yourself, do not set `external_api_alb`. Pass the hostname (or IP) into `gcp-host` as `api_connector_external_lb_host`. The two inputs are mutually exclusive. Bring-your-own does not provision the load balancer or require the extra IAM roles below.

### IAM when Terraform provisions the load balancer

Grant the Terraform runner these roles on the host project when `external_api_alb` is set (not required for bring-your-own):

| Role | Why |
|---|---|
| [Compute Network Admin](https://cloud.google.com/iam/docs/roles-permissions/compute#compute.networkAdmin) | Reserved global IP, serverless NEGs, backend services, URL map, HTTPS proxy, forwarding rule |
| [Compute Security Admin](https://cloud.google.com/iam/docs/roles-permissions/compute#compute.securityAdmin) | Cloud Armor (when an IP allowlist is set) and the self-signed certificate |
| [Certificate Manager Editor](https://cloud.google.com/iam/docs/roles-permissions/certificatemanager#certificatemanager.editor) | Google-managed TLS when `external_api_alb.domain` is set |

See [Getting Started](../getting-started.md#iam-permissions) and the [development doc](../../development/gcp-external-alb.md#iam-permissions-terraform-provisioner).

Uncomment the `external_api_alb` output in the example root if you need the host, reserved IP, DNS TODO, or self-signed CA certificate from `terraform output`.

## Custom audiences (required)

**Required after apply.** API connectors authenticate the caller with a Google identity token (`roles/run.invoker` on the Cloud Run service). Cloud Run accepts that token only when the token’s audience is the Cloud Run service URL, or a URL registered as a [custom audience](https://cloud.google.com/run/docs/configuring/custom-audiences).

Through the load balancer, the audience is the public URL Worklytics calls, not the default `*.run.app` URL. Register both of the following on each API connector service:

- `https://<region>-<project>.cloudfunctions.net/<function>`
- `https://<api-proxy-domain>/<function>`

`<api-proxy-domain>` is `external_api_alb.domain` for managed TLS, the reserved global IP for the self-signed proof of concept, or `api_connector_external_lb_host` for a load balancer you provisioned yourself.

If these audiences are missing, Cloud Run rejects the identity token. The failure is often HTTP 401 or 403. With `ALLOW_INTERNAL_AND_GCLB`, a missing or rejected token can also surface as HTTP 404.

As of 2026-09-15, Google's terraform resource `google_cloudfunctions2_function` has no custom-audience argument and the GCP console UX does not allow directly setting these values. The only solution is to use the `gcloud` CLI.Terraform does not set these. From the directory that contains `terraform.tfvars` (after `terraform apply`), run:

To ease this, we provide a script, which after your `terraform init` locally, you can find in `.terraform/psoxy/tools/gcp/configure-custom-audiences.sh`. Run that from the root of your terraform configuration and it will assist you by running the gcloud commands

The script checks that `terraform`, `gcloud`, and `jq` are installed, that `terraform.tfvars` is in the current directory, and that `gcloud` is authenticated. It reads the project, region, API proxy domain, and connector function names from Terraform output when those outputs exist, and otherwise from `terraform.tfvars` (`gcp_project_id`, `gcp_region`, `api_proxy_domain`, `external_api_alb.domain`, or `api_connector_external_lb_host`). If `gcp_region` is omitted from `terraform.tfvars` (the example default is `us-central1`), the script asks you to enter the region. It prints the resolved values and the `gcloud` updates it will run, and waits for confirmation before changing anything.

Re-run the script after you add an API connector. `--update-custom-audiences` replaces the custom-audience list on each service.
