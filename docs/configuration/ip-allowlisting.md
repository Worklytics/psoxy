# Client IP Allowlisting

Psoxy can restrict API data access and webhook ingestion to requests from known client IP addresses or CIDR blocks. This is an **optional** control on top of IAM authentication. Do not rely on it for authentication. It only increases the complexity of an attack. Client IP addresses can be spoofed.

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

For **additional** network ingress restriction on GCP (outside the proxy process), you can attach [Cloud Armor](https://cloud.google.com/run/docs/securing/cloud-armor) in front of a load balancer — see [Cloud Armor (GCP)](#cloud-armor-gcp) below. Related design notes: [GCP Private Service Connect and connectivity options](../development/gcp-private-service-connect.md#enhancing-public-internet-options-with-ip-allowlisting).

## AWS infrastructure-level detail

The core AWS module (`infra/modules/aws`) adds `ForAnyValue:IpAddress` / `aws:SourceIp` conditions when `allowed_data_access_ip_blocks` or `allowed_webhook_ip_blocks` is set:

- **API / bulk callers** — `api-caller` role assume-role policy (AWS principals and GCP workload-identity federated callers).
- **Webhook testing** — `webhook-test-caller` role assume-role policy when webhook testing is enabled.

This blocks role assumption from disallowed source IPs before the principal can invoke Lambdas or read sanitized buckets.

## Application-level detail (AWS and GCP)

Each API connector and webhook collector receives:

- `ALLOWED_DATA_ACCESS_IP_BLOCKS` — comma-separated list for REST API mode.
- `ALLOWED_WEBHOOK_IP_BLOCKS` — comma-separated list for webhook collector mode.

The proxy rejects requests with HTTP 403 when lockdown is enabled and the client IP cannot be determined or does not match an exact IP or CIDR entry. IPv4 and IPv6 are supported.

On **AWS**, the client address is API Gateway's `sourceIp` when that field is present. On **GCP**, the address is one hop from `X-Forwarded-For`, selected by `client_ip_x_forwarded_for_offset` (a negative offset from the end of the header, passed through as `CLIENT_IP_X_FORWARDED_FOR_OFFSET`). `-1` is the last address. The function module does not decide which hop a given deployment appends; you pass the offset that matches the proxies in front of the function. When the offset is omitted and an allowlist is set, the modules currently use `-1`. A future release will require an explicit offset whenever an allowlist is set.

`gcp-host` passes `-2` when an external Application Load Balancer fronts API connectors, because that load balancer appends the client it observed and then the forwarding-rule address. A direct Cloud Run URL leaves the offset unset, so an allowlist uses the last hop, which is the address Cloud Run appended. Webhook collectors are the same unless you set the offset.

`X-Forwarded-For` can be spoofed, in whole or in part. The offset only selects which entry the allowlist reads. If the header does not contain that entry, the client address is unknown and an allowlist rejects the request. See [FAQ - Security](../faq-security.md) and [Authentication and Authorization in GCP](../gcp/authentication-authorization.md).

Health checks are not subject to this gate (they run before IP enforcement) but still report `callerIp` in the response for verification.

## Operational notes

- Confirm the IP or CIDR your client actually presents to the proxy (health check `callerIp`, or Cloud Run / Lambda logs). Egress IPv4 and IPv6 may differ; Node.js clients often prefer IPv6 unless you force IPv4 (for example `NODE_OPTIONS='--dns-result-order=ipv4first'`).
- Do not rely on IP allowlisting for authentication. The proxy supports it only as an optional control that increases the complexity of an attack. Client IP addresses can be spoofed, so this check must not be the control you depend on. Authenticate callers with the host platform (for example GCP IAM). See [FAQ - Security](../faq-security.md).
- Fixed egress IPs from Worklytics may require a subscription add-on; contact [sales@worklytics.co](mailto:sales@worklytics.co). See also [FAQ - Security](../faq-security.md).

## Cloud Armor (GCP)

**Beta:** You can run a GCP Application Load Balancer (ALB) **without** Cloud Armor (open at the network layer; rely on IAM and application-layer auth). Cloud Armor IP filtering is optional and only applies when both of the following are true:

1. An external Application Load Balancer is enabled — set `external_api_alb` on `gcp-host`, or pass `api_connector_external_lb_host` for a customer-owned ALB (see [External Application Load Balancer](../gcp/guides/external-alb.md)).
2. `allowed_data_access_ip_blocks` is non-null (a non-empty CIDR list).

Then that list is applied as **Cloud Armor** rules on the ALB backends (network layer) in addition to the connector env vars (application layer). A mismatch between the list you configured and the IP you test from produces **`403 Forbidden`** from Cloud Armor before the proxy runs — typically a bare HTML page, not a Psoxy error body. With a `null` list, no Cloud Armor IP rules are attached even if the ALB is enabled.

After editing the allowlist in `terraform.tfvars`, run `terraform apply` and confirm the deployed rule (for example `gcloud compute security-policies rules describe 1000 ...`) includes your current IPv4 **and** IPv6 if either might be used. See [Troubleshooting](../gcp/guides/external-alb.md#troubleshooting) for TLS and 403 symptoms.
