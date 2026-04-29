# GCP Instance Concurrency

Psoxy on GCP Cloud Run supports handling multiple requests concurrently within a single
function instance. This reduces cold starts and improves cost efficiency for I/O-bound
proxy workloads.

## Variables

Both `gcp-proxy-api` and `gcp-webhook-collector` modules expose `instance_concurrency`.
Only `gcp-proxy-api` currently exposes `max_instance_count` as an input.

| Variable               | Type   | Default | Description                                        |
|------------------------|--------|---------|----------------------------------------------------|
| `instance_concurrency` | number | 5       | Max concurrent requests per instance               |
| `max_instance_count`   | number | 20      | Max number of Cloud Run instances (`gcp-proxy-api` only) |

> **Note**: `gcp-webhook-collector` currently uses a fixed internal `max_instance_count`
> of 5 and does not expose it as a module input.

### Example: Overriding defaults

```hcl
module "psoxy_gmail" {
  source = "../../modules/gcp-proxy-api"

  # ...

  instance_concurrency = 10
  max_instance_count   = 10
}
```

## Capacity Planning

Global concurrent request capacity = `instance_concurrency × max_instance_count`.

With defaults for the API proxy module: 5 × 20 = **100 concurrent requests**.

This is typically far more than needed — Worklytics data connectors are polled periodically
and rarely send more than a handful of concurrent requests per source.

## CPU Allocation

GCP Cloud Run requires at least 1 vCPU for concurrency > 1. The Terraform modules handle
this automatically:

- When `instance_concurrency > 1`: sets `available_cpu = "1"`
- When `instance_concurrency = 1`: leaves `available_cpu` at the Cloud Run default

> **Note**: Setting `available_cpu = "1"` means you are billed for CPU even while idle
> (CPU is always allocated). This is the required billing model for multi-threaded instances.
> For I/O-bound workloads like psoxy, sharing 1 vCPU across 5 requests is more efficient
> than running 5 separate instances.

## Bulk Proxy

The `gcp-proxy-bulk` module does **not** support `instance_concurrency > 1`. Bulk file
processing involves long-running, CPU-intensive transforms that are not suited for concurrent
sharing within a single instance.

## Reverting to Single-Request Mode

To disable concurrency and return to one-request-per-instance behavior:

```hcl
module "psoxy_gmail" {
  source = "../../modules/gcp-proxy-api"

  # ...

  instance_concurrency = 1
}
```

This removes the CPU allocation requirement, reverting to the default pay-per-request billing model.
