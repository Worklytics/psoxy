# External Application Load Balancer (ALB)

> **Status: Beta.** Provisioned by `gcp-host` when `external_api_alb` is set, or bring-your-own via `api_connector_external_lb_host`. Interfaces may change in a future release.

Use this when Worklytics must reach API connectors over the public internet through a hostname you control, or when your organization policy requires Cloud Run ingress of `internal-and-cloud-load-balancing`. You can also restrict which source IPs may call the load balancer.

This is the inbound path for API connectors. Outbound calls from the proxy to data sources, including a fixed egress IP, are configured separately with [VPC egress](vpc.md). Bulk connectors are unchanged. Webhook collectors keep their own endpoints. Leave `external_api_alb` unset to keep each API connector on its direct Cloud Functions URL (`*.run.app`).

Implementation notes live in [GCP External Application Load Balancer (ALB) + Cloud Armor](../../development/gcp-external-alb.md).

## Enable it

In `terraform.tfvars`:

```hcl
# Google-managed TLS. Create the DNS record from the apply TODO before testing.
external_api_alb = {
  domain = "proxy.example.com"
}

# Self-signed certificate on a reserved global IP (proof of concept)
# external_api_alb = {}
```

`terraform apply` then:

- Sets each API connector’s Cloud Functions ingress to `ALLOW_INTERNAL_AND_GCLB`, so internet clients reach the connector through the load balancer rather than `*.run.app`.
- Sets each connector’s public endpoint URL to `https://<host>/<function-name>/`. Test TODOs, generated test scripts, and the Worklytics "Psoxy Base URL" use that URL.
- For managed TLS (`domain` set), writes a DNS-setup TODO. Point that hostname at the reserved IP before you test or connect Worklytics. Certificate provisioning often takes 15–60 minutes after DNS propagates.

Uncomment the `external_api_alb` output in the example root if you need the host, reserved IP, DNS TODO, or self-signed CA certificate from `terraform output`.

### IAM when Terraform provisions the load balancer

Grant the Terraform runner these predefined roles on the host project when `external_api_alb` is set, in addition to the [roles required for a GCP deployment](../getting-started.md#iam-permissions). Bring-your-own (`api_connector_external_lb_host`) does not need them.

| Role | Why |
|---|---|
| [Compute Load Balancer Admin](https://cloud.google.com/iam/docs/roles-permissions/compute#compute.loadBalancerAdmin) (`roles/compute.loadBalancerAdmin`) | Reserved global IP (`compute.globalAddresses.*`), regional serverless NEGs (`compute.regionNetworkEndpointGroups.*`), the load balancer, and the self-signed certificate (`compute.sslCertificates.create` / `get` / `list` / `delete`, attached with `compute.targetHttpsProxies.setSslCertificates`) |
| [Compute Security Admin](https://cloud.google.com/iam/docs/roles-permissions/compute#compute.securityAdmin) (`roles/compute.securityAdmin`) | Cloud Armor, when you set an IP allowlist |
| [Certificate Manager Editor](https://cloud.google.com/iam/docs/roles-permissions/certificatemanager#certificatemanager.editor) (`roles/certificatemanager.editor`) | Google-managed TLS when `external_api_alb.domain` is set |

[Compute Engine API](https://console.cloud.google.com/apis/library/compute.googleapis.com) must be enabled. [Certificate Manager API](https://console.cloud.google.com/apis/library/certificatemanager.googleapis.com) must be enabled for managed TLS; Terraform attempts to enable it when `domain` is set.

To grant a custom role instead of these predefined roles, use `required_gcp_permissions_to_use_external_api_alb` from [`psoxy-constants`](../../../infra/modules/psoxy-constants).

## Custom audiences (required)

**Required after apply.** API connectors authenticate the caller with a Google identity token (`roles/run.invoker` on the Cloud Run service). Cloud Run accepts that token only when the token’s audience is the Cloud Run service URL, or a URL registered as a [custom audience](https://cloud.google.com/run/docs/configuring/custom-audiences).

Through the load balancer, the audience is the public URL Worklytics calls, not the default `*.run.app` URL. Register both of the following on each API connector service:

- `https://<region>-<project>.cloudfunctions.net/<function>`
- `https://<api-proxy-domain>/<function>`

`<api-proxy-domain>` is `external_api_alb.domain` for managed TLS, the reserved global IP for the self-signed proof of concept, or `api_connector_external_lb_host` for a load balancer you provisioned yourself.

If these audiences are missing, Cloud Run rejects the identity token. The failure is often HTTP 401 or 403. With `ALLOW_INTERNAL_AND_GCLB`, a missing or rejected token can also surface as HTTP 404.

As of 2026-09-15, Google's terraform resource `google_cloudfunctions2_function` has no custom-audience argument and the GCP console UX does not allow directly setting these values. The only solution is to use the `gcloud` CLI.

Specifically, you must run a gcloud command like the following for each API connector:

```shell
gcloud run services update "$FUNCTION_NAME" \
  --project="$PROJECT_ID" \
  --region="$REGION" \
  --set-custom-audiences="https://${REGION}-${PROJECT_ID}.cloudfunctions.net/${FUNCTION_NAME},https://${API_PROXY_DOMAIN}/${FUNCTION_NAME}"
```

To ease this, we provide a script. After `terraform init`, it is at `.terraform/modules/psoxy/tools/gcp/configure-custom-audiences.sh`. Run that from the root of your Terraform configuration and it will assist you by running the gcloud commands.

The script checks that `terraform`, `gcloud`, and `jq` are installed, that `terraform.tfvars` is in the current directory, and that `gcloud` is authenticated. It reads the project, region, API proxy domain, and connector function names from Terraform output when those outputs exist, and otherwise from `terraform.tfvars` (`gcp_project_id`, `gcp_region`, `api_proxy_domain`, `external_api_alb.domain`, or `api_connector_external_lb_host`). If `gcp_region` is omitted from `terraform.tfvars` (the example default is `us-central1`), the script asks you to enter the region. It prints the resolved values and the `gcloud` updates it will run, and waits for confirmation before changing anything.

Re-run the script after you add an API connector. `--set-custom-audiences` replaces the custom-audience list on each service.

## Restrict source IPs

To allow only known client IPs through the load balancer, set `allowed_data_access_ip_blocks` to a non-empty list of IPs or CIDR blocks. Worklytics can provide fixed egress IPs for your tenant as a paid add-on; contact [sales@worklytics.co](mailto:sales@worklytics.co).

```hcl
allowed_data_access_ip_blocks = [
  "203.0.113.10/32",
  "2001:db8::/32",
]
```

Terraform attaches Cloud Armor rules for that list on the load balancer, and the proxy enforces the same list inside each connector. Include every address the caller might use. IPv4 and IPv6 are checked separately.

Leave `allowed_data_access_ip_blocks` unset to leave the load balancer open to any source IP. Callers still authenticate to the connector. An empty list is rejected by Terraform.

See [Client IP Allowlisting](../../configuration/ip-allowlisting.md).

## Try it without a domain

```hcl
external_api_alb = {}
```

Clients use `https://<reserved-ip>/<function-name>/`. The certificate is issued for that IP. Test commands need `--allow-insecure-tls`, or `--cacert` with the PEM in `external_api_alb.self_signed_ca_cert` from `terraform output external_api_alb`. Use a domain and managed TLS for a Worklytics connection. Custom audiences are still required; use the reserved IP as `<api-proxy-domain>`.

## Use a load balancer you already operate

If you already front the API connectors with your own external Application Load Balancer, do not set `external_api_alb`. Pass the hostname or IP on the `gcp-host` module:

```hcl
api_connector_external_lb_host = "proxy.example.com"
```

In the GCP example this argument is commented next to `external_api_alb` in `main.tf`. The two settings cannot be used together.

You provide TLS, DNS, and any Cloud Armor policy. Route `https://<host>/<function-name>` and `https://<host>/<function-name>/*` to that connector. Terraform switches each API connector to load-balancer ingress and rewrites the URLs in the connection TODOs to `https://<host>/<function-name>/`. `allowed_data_access_ip_blocks` is still enforced by the proxy. Cloud Armor is not created for you in this mode. Register custom audiences for your hostname.

## Testing

Generated test scripts call the load balancer URL when either setting above is in effect. Hosts other than `*.run.app` need `-f gcp`. See [Psoxy test tool](../../guides/psoxy-test-tool.md).

If Cloud Armor is enabled, the machine running the test must be included in `allowed_data_access_ip_blocks`.

## Troubleshooting

### TLS connection resets

`ECONNRESET` or "socket disconnected before secure TLS connection was established" right after the first apply usually means the HTTPS load balancer is still propagating. Wait a few minutes, then:

```bash
curl -vk https://<host-or-ip>/<function-name>/
```

A completed TLS handshake with HTTP 403 or 404 means the load balancer is up. For the self-signed proof of concept, call the reserved IP, not a hostname, and pass `--allow-insecure-tls` or `--cacert`.

### 403 Forbidden

A minimal HTML page (`<title>403</title>403 Forbidden`) is Cloud Armor rejecting the source IP before the request reaches the proxy. A 403 response body from Psoxy means the request passed Cloud Armor and the connector's own allowlist rejected it. HTTP 401 or 403 after Cloud Armor allows the request can also mean the [custom audience](#custom-audiences-required) is missing.

1. Check the address you are connecting from. Add both if you are unsure which one clients use:

```bash
curl -4 -s ifconfig.me
curl -6 -s ifconfig.me
```

2. Confirm Cloud Armor has that address. The policy name is `{environment_name}-worklytics-ingress`, or `worklytics-ingress` when `environment_name` is unset:

```bash
gcloud compute security-policies rules describe 1000 \
  --security-policy=<policy-name> \
  --project=<gcp_project_id> \
  --format='yaml(match.config.srcIpRanges)'
```

3. Apply again after editing `allowed_data_access_ip_blocks`. The deployed rule must match `terraform.tfvars`.

Requests through the load balancer without a GCP identity token can return **404** rather than an authentication error. Test scripts obtain a token with `gcloud auth print-identity-token`.
