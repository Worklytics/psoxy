# Authentication and Authorization in GCP Deployments of Psoxy

This page provides an overview of how psoxy authenticates and confirms authorization of clients (Worklytics tenants) to access data for GCP-hosted deployments.

For general overview of how Psoxy is authorized to access data sources, and authenticates when making API calls to those sources, see [API Mode Authentication and Authorization](../authentication-authorization.md).

## Authentication

As Worklytics tenants run inside GCP, they are implicitly authenticated by GCP. No secrets or keys need be exchanged between your Worklytics tenant and your Psoxy instance. GCP can verify the identity of requests from Worklytics to your instance, just as it does between any process and resource within GCP.

## Authorization

Invocations of your proxy instances are authorized by the IAM policies you define in GCP. For API connectors, the shipped modules grant `roles/run.invoker` to each email in `worklytics_sa_emails` on the Cloud Function / Cloud Run service for your instance. That applies for direct `*.run.app` invocation and when API connectors are fronted by an [external Application Load Balancer (ALB)](../development/gcp-external-alb.md) (**beta**); the ALB forwards the caller's identity token and Cloud Run still enforces `roles/run.invoker`.

For the bulk data case, you grant the Storage Object Viewer role to your Worklytics tenant's GCP service account on the sanitized output bucket for your connector.

You can obtain the identity of your Worklytics tenant's GCP service account from the Worklytics portal. If your organization enforces domain-restricted sharing, you may need a project-level exception before Terraform can create cross-organization IAM bindings — see [Error 400: permitted customer](./troubleshooting.md#error-400--one-or-more-users-named-in-policy-do-not-belong-to-a-permitted-customer).

## Client IP allowlisting

When `allowed_data_access_ip_blocks` or `allowed_webhook_ip_blocks` is set in Terraform, the proxy enforces allowlists **inside the Cloud Function** via `ALLOWED_DATA_ACCESS_IP_BLOCKS` and `ALLOWED_WEBHOOK_IP_BLOCKS` environment variables. The shipped modules do **not** add source-IP IAM conditions on Cloud Run invoker bindings (GCP does not support that pattern on `roles/run.invoker`).

Worklytics can ensure fixed egress IP addresses for outbound requests from your tenant as a paid add-on. Contact [sales@worklytics.co](mailto:sales@worklytics.co) for details.

For network ingress filtering in front of Cloud Run (for example Cloud Armor on a load balancer), see [GCP External Application Load Balancer (ALB) + Cloud Armor](../development/gcp-external-alb.md) (beta) and [GCP Private Service Connect and connectivity options](../development/gcp-private-service-connect.md#enhancing-public-internet-options-with-ip-allowlisting).

See [Client IP Allowlisting](../configuration/ip-allowlisting.md).
