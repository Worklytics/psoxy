# GCP External ALB + Cloud Armor (TLS + IP allowlist)

> **Status**: Beta — composition pattern only; not a first-class module we commit to maintaining in this form
> **Last Updated**: 2026-07-21

## Motivation

Some customers need Worklytics to reach API connectors over the **public internet** from **static egress IPs** that the customer allowlists, while optionally using VPC egress (Direct VPC + Cloud NAT) for outbound calls to data sources.

This is **not** Private Service Connect / a regional internal load balancer (ILB). An internal ILB is for VPC-private consumers. Worklytics dialing in from the public internet needs a **global external Application Load Balancer** (`EXTERNAL_MANAGED`).

## Connectivity options (summary)

| Architecture | Ingress | TLS | IP restriction | VPC required? |
|---|---|---|---|---|
| Default | `*.run.app` | Google-managed | App-layer only (`allowed_data_access_ip_blocks`) | No |
| **External ALB + Cloud Armor** (this doc) | Global external ALB | Managed domain **or** self-signed PoC | Cloud Armor + app-layer | No (egress VPC optional) |
| mTLS | External ALB | Mutual TLS | Cloud Armor optional | No |
| PSC | Internal ILB + service attachment | Private | Inherent | Yes |

See also [GCP Private Service Connect and connectivity options](gcp-private-service-connect.md).

**Do not** use a regional internal LB for Worklytics static-IP egress over the internet.

## Current approach (v0.6.8): customer composition

Psoxy does **not** provision the ALB inside `gcp-host`. Customers (or the example template) own ALB/Cloud Armor Terraform in root `external-api-alb.tf`, then tell the modules the LB host.

1. Enable and adapt [`infra/examples-dev/gcp/external-api-alb.tf`](../../infra/examples-dev/gcp/external-api-alb.tf) (syncs to `psoxy-example-gcp`).
2. Set `allowed_data_access_ip_blocks` to Worklytics egress IPs (same list for Cloud Armor and the proxy).
3. Pass into `gcp-host`:

```hcl
api_connector_external_lb_host = var.api_proxy_domain != null ? var.api_proxy_domain : google_compute_global_address.api_proxy[0].address
```

When `api_connector_external_lb_host` is non-null, `gcp-host`:

- Sets each API connector’s Cloud Functions `ingress_settings` to `ALLOW_INTERNAL_AND_GCLB` (required for external ALB → Cloud Functions; **not** `ALLOW_INTERNAL_ONLY`, which is for PSC).
- Overrides public `endpoint_url` to `https://<host>/<function-name>/` (with `ALLOW_INTERNAL_AND_GCLB`, that is the internet-reachable path).
- Passes `external_lb_base_url = https://<host>` into connectors for test-script / TODO generation (deprecated plumbing; prefer lifting tests higher later).
- Leaves Pub/Sub push endpoints on the Cloud Function `*.run.app` URI.
- Leaves bulk connectors unchanged.

Worklytics `"Psoxy Base URL"` follows `endpoint_url` via the existing connection module wiring.

### Resource inventory (root composition)

From the commented example / internal PoCs:

- `google_compute_global_address`
- `google_compute_security_policy` + `google_compute_security_policy_rule` (allow `allowed_data_access_ip_blocks`, default deny)
- Per connector: serverless NEG + `EXTERNAL_MANAGED` backend service with `security_policy`
- URL map: path rules `/<function-name>` and `/<function-name>/*`
- TLS: Certificate Manager (domain) **or** Terraform `tls_*` + `google_compute_ssl_certificate` (PoC on global IP)
- `google_compute_global_forwarding_rule` on `:443`

Path routing relies on the proxy stripping the function-name prefix via `K_SERVICE` in `CloudFunctionRequest.getPath()`.

### Dual IP allowlist

`allowed_data_access_ip_blocks` does double duty:

1. **Cloud Armor** (network) — customer attaches the same CIDRs in `external-api-alb.tf`.
2. **Psoxy app check** — still set on connectors (covers direct `*.run.app` bypass until ingress is locked down).

### Testing

Generated test scripts use the ALB URL when the host is set. For self-signed PoC (IP host), scripts add `--allow-insecure-tls`. Prefer `--cacert` with the PEM from `external_alb_self_signed_ca_cert` when available. Non-`*.run.app` URLs also need `-f gcp`. See [Psoxy test tool](../guides/psoxy-test-tool.md).

Your caller IP must be in the Cloud Armor allowlist (and usually in `allowed_data_access_ip_blocks`) for local health checks through the ALB.

## Troubleshooting

### TLS errors (`ECONNRESET`, "socket disconnected before secure TLS connection was established")

These errors during `./test-*.sh` or `cli-call.js` are **usually not** a self-signed certificate problem when you already pass `--allow-insecure-tls` (or `--cacert` with the PEM from `external_alb_self_signed_ca_cert`). That flag disables certificate verification; a cert trust failure would look different.

More common causes:

1. **ALB still provisioning** — after the first `terraform apply` that creates the HTTPS proxy, self-signed cert, and global forwarding rule, wait several minutes and retry. Verify the handshake with curl before using `psoxy-test`:

```bash
curl -vk https://<alb-ip>/<environment>-<connector>/
```

You should complete TLS even if the HTTP status is 404 or 403.

2. **Wrong host** — PoC self-signed certs are issued for the **reserved global IP** (`terraform output external_alb_ip_address`), not a hostname, unless you switched to managed TLS with `api_proxy_domain`.

If curl completes TLS but `psoxy-test` does not, compare Node version and retry after the ALB finishes propagating.

### `403 Forbidden` (minimal HTML page: `<title>403</title>403 Forbidden`)

This response is almost always **Cloud Armor** denying your **source IP** before the request reaches Cloud Run. It is not an IAM or self-signed TLS issue.

`allowed_data_access_ip_blocks` is enforced in two places when the ALB composition is enabled:

| Layer | Where | Symptom if blocked |
|---|---|---|
| Network | Cloud Armor on the backend service | Minimal HTML `403 Forbidden` (no Psoxy body) |
| Application | `ALLOWED_DATA_ACCESS_IP_BLOCKS` on the connector | HTTP 403 from the proxy (after Cloud Armor allows the request) |

**Checklist:**

1. **Find the IP you are actually dialing from** — it must appear in `allowed_data_access_ip_blocks` in `terraform.tfvars` *and* in the deployed Cloud Armor rule. IPv4 and IPv6 are different; many clients use IPv4 even when you only captured IPv6 during setup:

```bash
curl -4 -s ifconfig.me    # IPv4
curl -6 -s ifconfig.me    # IPv6
```

2. **Compare to what Cloud Armor has deployed** (not just `terraform.tfvars`):

```bash
gcloud compute security-policies rules describe 1000 \
  --security-policy=<environment_name>-worklytics-ingress \
  --project=<gcp_project_id> \
  --format='yaml(match.config.srcIpRanges)'
```

3. **Compare to the connector app allowlist** (should match after apply):

```bash
gcloud run services describe <environment_name>-<connector> \
  --region=<gcp_region> --project=<gcp_project_id> \
  --format='value(spec.template.spec.containers[0].env)' | tr ';' '\n' | grep ALLOWED
```

4. **Confirm the IP Cloud Armor saw** — load balancer access logs include `remoteIp`:

```bash
gcloud logging read 'resource.type="http_load_balancer"' \
  --project=<gcp_project_id> --limit=5 \
  --format='table(httpRequest.remoteIp,httpRequest.status,httpRequest.requestUrl)'
```

5. **Re-apply after changing the allowlist** — if you edit `terraform.tfvars` but still see the old CIDRs in step 2, run `terraform apply`. The example `external-api-alb.tf` uses separate `google_compute_security_policy_rule` resources so `src_ip_ranges` updates are applied reliably; inline `rule` blocks on `google_compute_security_policy` are known to miss in-place IP changes in some provider versions.

**Quick fix for local testing:** add both your current IPv4 `/32` and IPv6 `/128` to `allowed_data_access_ip_blocks`, apply, then re-run the test script.

Without a GCP identity token, requests through the ALB may return **404** instead of 403 (ingress masking). Generated test scripts and `cli-call.js -f gcp` obtain a token via `gcloud auth print-identity-token`; ensure you are authenticated before testing.

## Future work: provision ALB inside `gcp-host`

Fold ALB + Cloud Armor provisioning into `gcp-host` (or a submodule it invokes) so customers set a high-level flag/object instead of copying ~300 lines of root Terraform. Left out of 0.6.8 on purpose; composition + `api_connector_external_lb_host` is the interim contract.

Possible future shape (illustrative only):

```hcl
external_api_alb = {
  domain = optional(string) # null => self-signed PoC on reserved IP
}
```

## Non-goals (this path)

- PSC producer module (internal ILB + `ALLOW_INTERNAL_ONLY`)
- mTLS on the external ALB (see [gcp-private-service-connect.md](gcp-private-service-connect.md))
- Provisioning customer VPC inside modules (keep egress compositional via `vpc_config`)
