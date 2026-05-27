# Client IP Allowlisting

Psoxy can restrict API data access and webhook ingestion to requests from known client IP addresses or CIDR blocks. This is a **defense-in-depth** control on top of IAM authentication — it limits *where* an already-authorized principal may call from, not *who* they are.

Configure allowlists in your root Terraform module (for example `infra/examples-dev/aws/terraform.tfvars` or `infra/examples-dev/gcp/terraform.tfvars`):

```hcl
allowed_data_access_ip_blocks = [
  "203.0.113.10",
  "2001:db8::/32",
]

allowed_webhook_ip_blocks = [
  "198.51.100.0/24",
]
```

Use `null` (the default) for no restriction. If you set either variable, the list must be non-empty.

## Enforcement by host platform

| Platform | Infra-level (IAM / host ACL) | Application-level (proxy) |
|----------|------------------------------|---------------------------|
| **AWS** | Yes — `aws:SourceIp` on caller role assume-role policies | Yes — `ALLOWED_DATA_ACCESS_IP_BLOCKS` / `ALLOWED_WEBHOOK_IP_BLOCKS` env vars |
| **GCP** | No — not available via Terraform modules in this repo | Yes — same env vars on Cloud Functions |

On **AWS**, both layers apply when you set the Terraform variables: callers must satisfy IAM IP conditions to assume the deployment's caller role *and* the proxy evaluates the client IP on each HTTP request.

On **GCP**, only the **application layer** is enforced by the shipped Terraform. Cloud Run IAM conditions do not support source-IP checks on `roles/run.invoker`, and GCS bucket IAM does not support the `inIpRange` patterns used on AWS. The proxy reads allowlists from environment variables set at deploy time.

For **additional** network ingress restriction on GCP (outside the proxy process), you can attach [Cloud Armor](https://cloud.google.com/run/docs/securing/cloud-armor) in front of a load balancer — see [GCP Private Service Connect and connectivity options](../development/gcp-private-service-connect.md#enhancing-public-internet-options-with-ip-allowlisting). That is separate from the `allowed_*_ip_blocks` Terraform variables.

## AWS infrastructure-level detail

The core AWS module (`infra/modules/aws`) adds `ForAnyValue:IpAddress` / `aws:SourceIp` conditions when `allowed_data_access_ip_blocks` or `allowed_webhook_ip_blocks` is set:

- **API / bulk callers** — `api-caller` role assume-role policy (AWS principals and GCP workload-identity federated callers).
- **Webhook testing** — `webhook-test-caller` role assume-role policy when webhook testing is enabled.

This blocks role assumption from disallowed source IPs before the principal can invoke Lambdas or read sanitized buckets.

## Application-level detail (AWS and GCP)

Each API connector and webhook collector receives:

- `ALLOWED_DATA_ACCESS_IP_BLOCKS` — comma-separated list for REST API mode.
- `ALLOWED_WEBHOOK_IP_BLOCKS` — comma-separated list for webhook collector mode.

The proxy rejects requests with HTTP 403 when lockdown is enabled and the client IP cannot be determined or does not match an exact IP or CIDR entry. IPv4 and IPv6 are supported; comma-separated `X-Forwarded-For` values use the first hop.

Health checks are not subject to this gate (they run before IP enforcement) but still report `callerIp` in the response for verification.

## Operational notes

- Confirm the IP or CIDR your client actually presents to the proxy (health check `callerIp`, or Cloud Run / Lambda logs). Egress IPv4 and IPv6 may differ; Node.js clients often prefer IPv6 unless you force IPv4 (for example `NODE_OPTIONS='--dns-result-order=ipv4first'`).
- IP allowlisting is not authentication. IPs can be spoofed in some paths; treat this as a supplementary control.
- Fixed egress IPs from Worklytics may require a subscription add-on; contact [sales@worklytics.co](mailto:sales@worklytics.co). See also [FAQ - Security](../faq-security.md).
