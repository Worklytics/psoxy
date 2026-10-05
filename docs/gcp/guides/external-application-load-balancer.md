# External Application Load Balancer

**Beta.** Setting `external_api_alb` provisions a global external HTTPS load balancer in front of your API connectors. The settings below may change in a future release.

Use this when Worklytics must reach your API connectors over the public internet through a hostname you control, or when your organization policy disallows public `*.run.app` URLs and requires Cloud Run ingress of `internal-and-cloud-load-balancing`. You can also restrict which source IPs may call the load balancer.

This is the inbound path for API connectors. Outbound calls from the proxy to data sources, including a fixed egress IP, are configured separately with [VPC egress](vpc.md). Bulk connectors are unchanged. Webhook collectors keep their own endpoints.

## Enable managed TLS

In `terraform.tfvars`:

```hcl
external_api_alb = {
  domain = "proxy.example.com"
}
```

`domain` is the hostname Worklytics and your tests will use. Leave `external_api_alb` unset to keep each API connector on its direct Cloud Functions URL.

The Terraform identity that applies this needs these roles on the proxy project, in addition to the [roles required for a GCP deployment](../getting-started.md#iam-permissions):

| Role | Used for |
|---|---|
| [Compute Network Admin](https://cloud.google.com/iam/docs/roles-permissions/compute#compute.networkAdmin) (`roles/compute.networkAdmin`) | Reserved global IP and the load balancer |
| [Compute Security Admin](https://cloud.google.com/iam/docs/roles-permissions/compute#compute.securityAdmin) (`roles/compute.securityAdmin`) | Cloud Armor, when you set an IP allowlist |
| [Certificate Manager Editor](https://cloud.google.com/iam/docs/roles-permissions/certificatemanager#certificatemanager.editor) (`roles/certificatemanager.editor`) | Google-managed certificate for `domain` |

[Compute Engine API](https://console.cloud.google.com/apis/library/compute.googleapis.com) and [Certificate Manager API](https://console.cloud.google.com/apis/library/certificatemanager.googleapis.com) must be enabled. A missing role usually surfaces as `403` on `compute.globalAddresses.create`, `compute.sslCertificates.create`, or a Certificate Manager resource during `terraform apply`.

After apply:

1. Create the DNS record from the TODO file `TODO * - configure DNS for API connector load balancer.md` (an `A` record for your domain pointing at the reserved global IP).
2. Wait for the Google-managed certificate. Provisioning often takes 15–60 minutes after DNS propagates.
3. Use the connector URLs from the Worklytics connection TODOs. They look like `https://proxy.example.com/<function-name>/`. Give Worklytics those URLs.

To print the hostname, reserved IP, and DNS instructions from Terraform, uncomment the `external_api_alb` output in the example `main.tf` and run `terraform output external_api_alb`.

## Restrict source IPs

To allow only known client IPs through the load balancer, set `allowed_data_access_ip_blocks` to a non-empty list of IPs or CIDR blocks. Worklytics can provide fixed egress IPs for your tenant as a paid add-on; contact [sales@worklytics.co](mailto:sales@worklytics.co).

```hcl
external_api_alb = {
  domain = "proxy.example.com"
}

allowed_data_access_ip_blocks = [
  "203.0.113.10/32",
  "2001:db8::/32",
]
```

Terraform attaches Cloud Armor rules for that list on the load balancer, and the proxy enforces the same list inside each connector. Include every address the caller might use. IPv4 and IPv6 are checked separately.

Leave `allowed_data_access_ip_blocks` unset to leave the load balancer open to any source IP. Callers still authenticate to the connector. An empty list is rejected by Terraform.

See [Client IP Allowlisting](../../configuration/ip-allowlisting.md) for how the proxy applies the list.

## Try it without a domain

For a short proof of concept, reserve an IP and serve a self-signed certificate:

```hcl
external_api_alb = {}
```

Clients use `https://<reserved-ip>/<function-name>/`. The certificate is issued for that IP. Test commands need `--allow-insecure-tls`, or `--cacert` with the PEM in `external_api_alb.self_signed_ca_cert` from `terraform output external_api_alb`. Use a domain and managed TLS for a Worklytics connection.

## Use a load balancer you already operate

If you already front the API connectors with your own external Application Load Balancer, do not set `external_api_alb`. Pass the hostname or IP on the `gcp-host` module:

```hcl
api_connector_external_lb_host = "proxy.example.com"
```

In the GCP example this argument is commented next to `external_api_alb` in `main.tf`. The two settings cannot be used together.

You provide TLS, DNS, and any Cloud Armor policy. Route `https://<host>/<function-name>` and `https://<host>/<function-name>/*` to that connector. Terraform switches each API connector to load-balancer ingress and rewrites the URLs in the connection TODOs to `https://<host>/<function-name>/`. `allowed_data_access_ip_blocks` is still enforced by the proxy. Cloud Armor is not created for you in this mode.

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

A minimal HTML page (`<title>403</title>403 Forbidden`) is Cloud Armor rejecting the source IP before the request reaches the proxy. A 403 response body from Psoxy means the request passed Cloud Armor and the connector's own allowlist rejected it.

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
