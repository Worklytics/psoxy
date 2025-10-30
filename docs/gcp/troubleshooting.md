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

This may be due to an [Organization Policy](https://cloud.google.com/resource-manager/docs/organization-policy/overview) that restricts the domains that can be used in IAM policies. See https://cloud.google.com/resource-manager/docs/organization-policy/restricting-domains

You may need define an exception for the GCP project in which you're deploying the proxy, or add the domain of your Worklytics Tenant SA to the list of allowed domains.

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

You will likely see MANY changes. These are caused by the provider version difference and should be benign. The vast majority are label changes; we utilize the `default_labels` functionality in google provider `5.x` to label all the infra created by this configuration;

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

