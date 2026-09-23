# GCP Trouble Shooting

Tips and tricks for using GCP as to host the proxy.

## GCloud CLI client blocked by Organization policy

Some orgs have policies that block authentication of the GCloud CLI client, requiring you to contact your IT team and have it added to an approved list. Apart from that, there are several possibilities:

1. use the GCP Cloud Shell (via GCP web console). `gcloud` is pre-installed and pre-authorized as your Google user in the Cloud Shell.
2. use a VM in GCP Compute Engine, with the VM running as a sufficiently privileged service account. In such a scenario, `gcloud` will be pre-authenticated by GCP on the VM as that service account.
3. create credentials within the project itself:
   - enable IAM API and Cloud Resource Manager API within the project
   - create OAuth credentials for a 'desktop application' within the target GCP project
   - download the `client-secrets.json` file to your environment
   - run `gcloud auth application-default login --client-id-file=/path/to/client-secrets.json`

## GCP rejects calls because APIs disabled on target project

Terraform relies on GCP's REST APIs for its operations. If these APIs are disabled either the target project OR the project in which the identity (service account, OAuth client) under which you're running terraform resides, you may get an error.

The solution is to enable APIs via the Cloud Console, specifically:

- IAM API
- Cloud Resource Manager API

## GCP Terraform State Inconsistencies

If some resources seem to not be properly provisioned, try `terraform taint` or `terraform state rm`, to force re-creation. Use `terrafrom state list | grep` to search for specific resource ids.

## Error 400 : One or more users named in policy do not belong to a permitted Customer

If you receive an error such as:

```
Error: Error applying IAM policy for cloudfunctions cloudfunction googleapi: Error 400: One or more users named in the policy do not belong to a permitted customer.
```

This may be due to an [Organization Policy](https://cloud.google.com/resource-manager/docs/organization-policy/overview) that restricts the domains that can be used in IAM policies. See [https://cloud.google.com/resource-manager/docs/organization-policy/restricting-domains](https://cloud.google.com/resource-manager/docs/organization-policy/restricting-domains).

You may need define an exception for the GCP project in which you're deploying the proxy, or add the domain of your Worklytics Tenant SA to the list of allowed domains.

## Error 400: Validation failed for trigger, Permission denied while using the Eventarc Service Agent

If you receive an error such as:

```
Error: Error creating function: googleapi: Error 400: Validation failed for trigger projects/my-project-id/locations/us-central1/triggers/survey-495732: Invalid resource state for "": Permission denied while using the Eventarc Service Agent. If you recently started to use Eventarc, it may take a few minutes before all necessary permissions are propagated to the Service Agent. Otherwise, verify that it has Eventarc Service Agent role.
```

This error occurs when the Eventarc Service Agent doesn't have the necessary permissions to create triggers for Cloud Functions. The Eventarc Service Agent is a Google-managed service account that handles event routing.

In our experience, this DOES resolve itself after a few minutes; so wait and try again. If still fails, confirm Eventarc service is activated in the project.

## Warning like 'Failed to find a usable hardware address from the network interfaces; using random bytes: '

This is benign and can be safely ignored.

## Perpetual Changes to `docker_repostory`, `environment_variables.LOG_EXECUTION_ID`

We've observed in some customers, where after upgrading proxy versions 0.5.x, they see perpetual changes in their Terraform plan.

To solve this, you should upgrade your `google` provider.

1. find the `google` provider version constraint at the top of your `main.tf`; it should look something like:

```hcl
terraform {
  required_providers {
    google = {
      version = "> 3.7.4, <= 5.0"
    }
  }

```

Change that to:

```hcl
terraform {
  required_providers {
    google = {
      version = "~> 5.0"
    }
  }
```

2. `terraform init --upgrade` and `terraform apply`

You will likely see MANY changes. These are caused by the provider version difference and should be benign. The vast majority are label changes; we utilize the `default_labels` functionality in the google provider `7.x` at the root `provider "google"` block to label all the infra created by this configuration;

## Permission denied creating or updating Cloud Functions / Artifact Registry

When `terraform apply` fails with `Error 403` and names a permission, grant that permission to the identity running Terraform (your user or `gcp_terraform_sa_account_email` service account).

Common examples:

```
Error: Error creating Artifact Registry Repository: googleapi: Error 403: Permission 'artifactregistry.repositories.create' denied ...
```

```
Error: Error updating function "projects/.../functions/outlook-mail": googleapi: Error 403: Permission '...' denied ...
```

**Fix:** grant the missing permission. Our [GCP prerequisites](./getting-started.md#iam-permissions) list the least-privileged predefined roles that cover a full Psoxy deployment; the [`psoxy-constants`](../../infra/modules/psoxy-constants) module exposes the same role list for bootstrapping (`required_gcp_roles_to_provision_host`). In particular, initial deploys often fail until the Terraform runner has **Artifact Registry Editor** (`roles/artifactregistry.editor`) — Terraform provisions the Docker repository used by Cloud Functions Gen 2.

If you use a custom IAM role instead of the predefined roles, compare your role against `required_gcp_perms_to_provision_host` from `psoxy-constants`. If you use [VPC egress](./vpc.md), you may also need `roles/compute.networkAdmin` (when Terraform creates the VPC) and, for Shared VPC, `roles/compute.networkUser` on the host-project subnet for the Cloud Run service agent.

If you set `external_api_alb` on `gcp-host`, grant `required_gcp_roles_to_use_external_api_alb` (or `required_gcp_permissions_to_use_external_api_alb` for a custom role). Typical missing-permission errors:

```
Error: Error creating GlobalAddress: googleapi: Error 403: Required 'compute.globalAddresses.create' permission ...
```

→ **Compute Network Admin** (`roles/compute.networkAdmin`).

```
Error: Error creating SslCertificate: googleapi: Error 403: Required 'compute.sslCertificates.create' permission ...
```

→ **Compute Security Admin** (`roles/compute.securityAdmin`).

See [GCP External ALB](../development/gcp-external-alb.md#iam-permissions-terraform-provisioner).

## Organization policy blocks Cloud Run networking

Some enterprises enforce Cloud Run networking via [organization policy](https://cloud.google.com/resource-manager/docs/organization-policy/overview), for example:

- [`constraints/run.allowedVPCEgress`](https://cloud.google.com/run/docs/securing/using-vpc-service-controls) — may require all outbound traffic through a VPC (`all-traffic`) while blocking deployments that omit VPC egress.
- [`constraints/run.allowedIngress`](https://cloud.google.com/run/docs/securing/private-networking) — may restrict ingress to `internal-and-cloud-load-balancing`, which disallows the default public `*.run.app` URL as the only entry point.

When policy and your Terraform configuration disagree, `terraform apply` may fail updating API connectors (for example `outlook-cal`, `outlook-mail`, `zoom`). One symptom is:

```
Error: Error updating function "projects/.../functions/outlook-mail": googleapi: Error 400: Could not update Cloud Run service ... spec.template.metadata.annotations: The run.googleapis.com/vpc-access-egress annotation cannot be set without also setting the run.googleapis.com/vpc-access-connector annotation or the run.googleapis.com/network-interfaces annotation.
```

**Fix (preferred if your security team allows it):** request a **project-level exception** (or folder exception) so Psoxy can use the networking settings documented in [VPC configuration](./vpc.md) — Direct VPC egress for fixed outbound IPs, and default or ALB-compatible ingress as appropriate.

**Fix (ingress restriction without VPC exception):** deploy an **external Application Load Balancer (ALB)** (**beta**) in front of API connectors — set `external_api_alb` on `gcp-host`, or pass a customer-owned host via `api_connector_external_lb_host`. That sets `ingress_settings` to `ALLOW_INTERNAL_AND_GCLB`, which satisfies `internal-and-cloud-load-balancing` while Worklytics reaches connectors through the ALB. See [GCP External Application Load Balancer (ALB) + Cloud Armor](../development/gcp-external-alb.md). VPC egress (`vpc_config`) is orthogonal to the ALB path.

If your org mandates VPC egress for all Cloud Run traffic, keep `vpc_config` populated with valid `network` and `subnet` values and ensure the exception (or policy values) allow Direct VPC egress with `all-traffic`.

## Error 400: `vpc-access-egress` annotation cannot be set without connector or network-interfaces

If you remove Direct VPC egress or a Serverless VPC Access connector from your configuration (for example, by commenting out `vpc_config` in `terraform.tfvars`), `terraform apply` may fail with:

```
Error 400: Could not update Cloud Run service ... spec.template.metadata.annotations: The run.googleapis.com/vpc-access-egress annotation cannot be set without also setting the run.googleapis.com/vpc-access-connector annotation or the run.googleapis.com/network-interfaces annotation.
```

This is a GCP limitation: Cloud Functions gen2 cannot drop VPC egress settings through an in-place service update. Terraform attempts to remove `direct_vpc_network_interface` from the function, but the underlying Cloud Run service still has a stale egress annotation until the function is recreated.

**Fix:** destroy the affected Cloud Function resources in Terraform, then apply to recreate them without VPC networking.

1. Ensure `vpc_config` is removed or set to `null` in your configuration.
2. Find the resource addresses:

```bash
terraform state list | grep google_cloudfunctions2_function
```

3. Destroy each affected function. Examples using the standard `module "psoxy"` layout from our GCP examples:

```bash
terraform destroy -target='module.psoxy.module.api_connector["gcal"].google_cloudfunctions2_function.function'
terraform destroy -target='module.psoxy.module.api_connector["msft-teams"].google_cloudfunctions2_function.function'
terraform destroy -target='module.psoxy.module.webhook_collector["llm-portal"].google_cloudfunctions2_function.function'
```

Repeat for every connector that had VPC egress enabled. Adjust the module path if your root module is not named `psoxy`, and use the connector map keys from your `terraform.tfvars`.

4. Recreate:

```bash
terraform apply
```

Alternatively, a single apply can replace a function without a separate destroy step:

```bash
terraform apply -replace='module.psoxy.module.api_connector["gcal"].google_cloudfunctions2_function.function'
```

See [VPC configuration](./vpc.md#removing-vpc-egress) for background on Direct VPC egress and removal.

## Bulk processing failures

If you need to re-trigger bulk processing of objects that have already been written to GCS (e.g., for webhook collectors), you can use the `replay-gcs-writes.sh` script.

This script uses `gsutil rewrite -kO` to replay write events on GCS objects, which triggers Cloud Storage write events that will cause the Cloud Function to re-process those objects.

```bash
# Re-trigger processing for all objects created in the last week
./tools/gcp/replay-gcs-writes.sh my-bucket-name

# Re-trigger processing for objects created since a specific date
./tools/gcp/replay-gcs-writes.sh my-bucket-name 2024-01-01T00:00:00Z

# Re-trigger processing for a single object
./tools/gcp/replay-gcs-writes.sh gs://my-bucket-name/path/to/object.json
```

