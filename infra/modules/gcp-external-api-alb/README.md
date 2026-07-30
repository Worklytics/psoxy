# gcp-external-api-alb

Provisions a global external Application Load Balancer (optional Cloud Armor) in front of GCP API connector Cloud Run / Cloud Functions services.

Invoked by `gcp-host` when `external_api_alb` is set. The caller reserves the global IP address (to resolve connector endpoint hosts without a Terraform module cycle) and should `depends_on` the API connector modules so serverless NEGs attach after services exist.

See [docs/development/gcp-external-alb.md](../../../docs/development/gcp-external-alb.md).
