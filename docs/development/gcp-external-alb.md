# GCP External Application Load Balancer (ALB) + Cloud Armor (TLS + optional IP allowlist)

> **Status**: **Beta** — provisioned by `gcp-host` when `external_api_alb` is set (or BYO via `api_connector_external_lb_host`). Interfaces and resource shapes may change in a future release.
> **Last Updated**: 2026-07-30

## Motivation

Some customers need Worklytics to reach API connectors over the **public internet**, optionally from **static egress IPs** that the customer allowlists, while optionally using VPC egress (Direct VPC + Cloud NAT) for outbound calls to data sources.

This is **not** Private Service Connect / a regional internal load balancer (ILB). An internal ILB is for VPC-private consumers. Worklytics dialing in from the public internet needs a **global external Application Load Balancer (ALB)** (`EXTERNAL_MANAGED`).

## Connectivity options (summary)

| Architecture | Ingress | TLS | IP restriction | VPC required? |
|---|---|---|---|---|
| Default | `*.run.app` | Google-managed | App-layer only (`allowed_data_access_ip_blocks`) | No |
| **External Application Load Balancer (ALB)** (this doc) | Global external ALB | Managed domain **or** self-signed PoC | Optional Cloud Armor + app-layer | No (egress VPC optional) |
| mTLS | External ALB | Mutual TLS | Cloud Armor optional | No |
| PSC | Internal ILB + service attachment | Private | Inherent | Yes |

See also [GCP Private Service Connect and connectivity options](gcp-private-service-connect.md).

**Do not** use a regional internal LB for Worklytics static-IP egress over the internet.

## Current approach: `gcp-host` first-class option

Set `external_api_alb` on `gcp-host` (example root passes `var.external_api_alb`):

```hcl
# terraform.tfvars — self-signed PoC on reserved global IP
external_api_alb = {}

# or managed TLS
external_api_alb = {
  domain = "proxy.example.com"
}
```

Leave `external_api_alb = null` (default) to expose API connectors via their direct Cloud Functions / Cloud Run URLs (`*.run.app`), without an external Application Load Balancer.

Optionally set `allowed_data_access_ip_blocks` to Worklytics egress IPs (same list for Cloud Armor and the proxy). Leave `null` for open ingress through the ALB (PoC / rely on IAM + app auth). An empty list is invalid.

### IAM permissions (Terraform provisioner)

These apply when **`external_api_alb` is set** and Terraform provisions the ALB (not when you use `api_connector_external_lb_host` for a customer-owned load balancer).

Grant the Terraform runner the following predefined roles on the host project, or an equivalent custom role built from the permission list in [`psoxy-constants`](../../infra/modules/psoxy-constants) (`required_gcp_roles_to_use_external_api_alb` / `required_gcp_permissions_to_use_external_api_alb`):

| Role | Why |
|---|---|
| [Compute Network Admin](https://cloud.google.com/iam/docs/roles-permissions/compute#compute.networkAdmin) (`roles/compute.networkAdmin`) | Reserved global IP (`compute.globalAddresses.*`), serverless NEGs, backend services, URL map, HTTPS proxy, global forwarding rule |
| [Compute Security Admin](https://cloud.google.com/iam/docs/roles-permissions/compute#compute.securityAdmin) (`roles/compute.securityAdmin`) | Cloud Armor security policies (when `allowed_data_access_ip_blocks` is set); self-signed `google_compute_ssl_certificate` for PoC TLS |
| [Certificate Manager Editor](https://cloud.google.com/iam/docs/roles-permissions/certificatemanager#certificatemanager.editor) (`roles/certificatemanager.editor`) | Google-managed TLS when `external_api_alb.domain` is set (Certificate Manager certificate + map) |

Common `403` errors during `terraform apply` if these are missing:

```
Error: Error creating GlobalAddress: googleapi: Error 403: Required 'compute.globalAddresses.create' permission ...
```

→ grant **Compute Network Admin** (or include `compute.globalAddresses.create` in a custom role).

```
Error: Error creating SslCertificate: googleapi: Error 403: Required 'compute.sslCertificates.create' permission ...
```

→ grant **Compute Security Admin** (or include `compute.sslCertificates.create` in a custom role).

When using managed TLS (`domain` set), failures on `google_certificate_manager_*` resources require **Certificate Manager Editor** (or the `certificatemanager.*` permissions listed in `required_gcp_permissions_to_use_external_api_alb`).

Terraform also enables `certificatemanager.googleapis.com` when `domain` is set; ensure [Compute Engine API](https://console.cloud.google.com/apis/library/compute.googleapis.com) (`compute.googleapis.com`) is enabled (listed in [GCP getting started](../gcp/getting-started.md#apis)).

### Bring-your-own Application Load Balancer (ALB)

If you already provision an external Application Load Balancer yourself, do **not** set `external_api_alb`. Instead pass:

```hcl
api_connector_external_lb_host = "proxy.example.com"
```

These two inputs are mutually exclusive.

When an external LB host is effective (provisioned or BYO), `gcp-host`:

- Sets each API connector’s Cloud Functions `ingress_settings` to `ALLOW_INTERNAL_AND_GCLB` (required for external ALB → Cloud Functions; **not** `ALLOW_INTERNAL_ONLY`, which is for PSC).
- Overrides public `endpoint_url` to `https://<host>/<function-name>/` (with `ALLOW_INTERNAL_AND_GCLB`, that is the internet-reachable path).
- Passes `external_lb_base_url = https://<host>` into connectors for test-script / TODO generation.
- Leaves Pub/Sub push endpoints on the Cloud Function `*.run.app` URI.
- Leaves bulk connectors unchanged.

Worklytics `"Psoxy Base URL"` follows `endpoint_url` via the existing connection module wiring.

### What gets provisioned

Implemented by `infra/modules/gcp-external-api-alb` (invoked from `gcp-host`):

- `google_compute_global_address` (reserved in `gcp-host` so connector URLs can use the IP without a module cycle)
- **When `allowed_data_access_ip_blocks` is non-null:** Cloud Armor security policy + allow/deny rules. When the list is `null`, no policy is attached — all source IPs may reach the ALB.
- Per connector: serverless NEG + `EXTERNAL_MANAGED` backend service
- URL map: path rules `/<function-name>` and `/<function-name>/*`
- TLS: Certificate Manager (domain) **or** Terraform `tls_*` + `google_compute_ssl_certificate` (PoC on global IP)
- `google_compute_global_forwarding_rule` on `:443`

Path routing relies on the proxy stripping the function-name prefix via `K_SERVICE` in `CloudFunctionRequest.getPath()`.

Useful output from `gcp-host`: `external_api_alb` (object with `host`, `ip_address`, `todo_dns_setup`, `self_signed_ca_cert`; null when unused). The example root keeps that output commented out — uncomment if you need it.

### IP Allowlist Enforcement

When `external_api_alb` is enabled, GCP-hosted proxy instances can **also** enforce IP allowlists at the infrastructure level by provisioning Cloud Armor rules from `allowed_data_access_ip_blocks` (in addition to the application-layer check on connectors, which covers direct `*.run.app` bypass until ingress is locked down).

When `allowed_data_access_ip_blocks` is `null`, neither layer restricts by source IP (the ALB remains open at the network layer).

### Testing

Generated test scripts use the ALB URL when an external LB base URL is set (provisioned via `external_api_alb` or BYO via `api_connector_external_lb_host`). For self-signed PoC (IP host), scripts add `--allow-insecure-tls`. Prefer `--cacert` with the PEM from `external_api_alb.self_signed_ca_cert` when available. Non-`*.run.app` URLs also need `-f gcp`. See [Psoxy test tool](../guides/psoxy-test-tool.md).

When Cloud Armor allowlisting is enabled, your caller IP must be in `allowed_data_access_ip_blocks` for local health checks through the ALB.

## Troubleshooting

### TLS errors (`ECONNRESET`, "socket disconnected before secure TLS connection was established")

These errors during `./test-*.sh` or `cli-call.js` are **usually not** a self-signed certificate problem when you already pass `--allow-insecure-tls` (or `--cacert` with the PEM from `external_api_alb.self_signed_ca_cert`). That flag disables certificate verification; a cert trust failure would look different.

More common causes:

1. **ALB still provisioning** — after the first `terraform apply` that creates the HTTPS proxy, self-signed cert, and global forwarding rule, wait several minutes and retry. Verify the handshake with curl before using `psoxy-test`:

```bash
curl -vk https://<alb-ip>/<cloud-function-name>/
```

You should complete TLS even if the HTTP status is 404 or 403.

2. **Wrong host** — PoC self-signed certs are issued for the **reserved global IP** (`terraform output -json external_api_alb` → `ip_address`), not a hostname, unless you set `external_api_alb.domain`.

If curl completes TLS but `psoxy-test` does not, compare Node version and retry after the ALB finishes propagating.

### `403 Forbidden` (minimal HTML page: `<title>403</title>403 Forbidden`)

This response is almost always **Cloud Armor** denying your **source IP** before the request reaches Cloud Run. It is not an IAM or self-signed TLS issue. If `allowed_data_access_ip_blocks` is `null`, Cloud Armor rules are not provisioned — a HTML 403 from Armor should not occur for IP reasons.

`allowed_data_access_ip_blocks` is enforced in two places when the list is non-null:

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
  --security-policy=<environment_id_prefix>worklytics-ingress \
  --project=<gcp_project_id> \
  --format='yaml(match.config.srcIpRanges)'
```

(`environment_id_prefix` is `{environment_name}-` when `environment_name` is set, or empty when unset — e.g. `psoxy-dev-erik-worklytics-ingress` vs `worklytics-ingress`.)

3. **Compare to the connector app allowlist** (should match after apply):

```bash
gcloud run services describe <cloud-function-name> \
  --region=<gcp_region> --project=<gcp_project_id> \
  --format='value(spec.template.spec.containers[0].env)' | tr ';' '\n' | grep ALLOWED
```

4. **Confirm the IP Cloud Armor saw** — load balancer access logs include `remoteIp`:

```bash
gcloud logging read 'resource.type="http_load_balancer"' \
  --project=<gcp_project_id> --limit=5 \
  --format='table(httpRequest.remoteIp,httpRequest.status,httpRequest.requestUrl)'
```

5. **Re-apply after changing the allowlist** — if you edit `terraform.tfvars` but still see the old CIDRs in step 2, run `terraform apply`. The module uses separate `google_compute_security_policy_rule` resources so `src_ip_ranges` updates are applied reliably; inline `rule` blocks on `google_compute_security_policy` are known to miss in-place IP changes in some provider versions.

**Quick fix for local testing:** add both your current IPv4 `/32` and IPv6 `/128` to `allowed_data_access_ip_blocks`, apply, then re-run the test script.

Without a GCP identity token, requests through the ALB may return **404** instead of 403 (ingress masking). Generated test scripts and `cli-call.js -f gcp` obtain a token via `gcloud auth print-identity-token`; ensure you are authenticated before testing.

## Migrating from root `external-api-alb.tf` (pre–first-class)

Older betas composed ALB resources in the example root. Those resources now live under `module.psoxy` (`google_compute_global_address.api_connector_alb` and `module.external_api_alb`). If you applied the root composition, either destroy the old root ALB resources and re-apply with `external_api_alb = {}`, or `terraform state mv` addresses into the new module paths before apply.

## Non-goals (this path)

- PSC producer module (internal ILB + `ALLOW_INTERNAL_ONLY`)
- mTLS on the external ALB (see [gcp-private-service-connect.md](gcp-private-service-connect.md))
- Provisioning customer VPC inside modules (keep egress compositional via `vpc_config`)
