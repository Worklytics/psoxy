# Upgrade Proxy Versions

Prior to any upgrade, please determine your current version and target version, then review the [`CHANGELOG.md`](https://github.com/Worklytics/psoxy/blob/main/CHANGELOG.md) and [release notes](https://github.com/Worklytics/psoxy/releases) your target version, and all intermediary versions, for any significant changes that may require additional action on your part.

Generally speaking, prior to a `v1`, we strive to follow semantic versioning as follows: `v{x}.{y}.{z}`

## Deployments Using Our Examples (from `v0.4.30` and later)

To ease upgrading versions, our example repos ([psoxy-example-aws](https://github.com/Worklytics/psoxy-example-aws) or [psoxy-example-gcp](https://github.com/Worklytics/psoxy-example-gcp) since `v0.4.30` include a script to update all the version references in your configuration.

```shell
./upgrade-terraform-modules v0.5.7
```

This will update all the versions references throughout your example, and offer you a command to revert if you later wish to do so.  A `terraform init` with the appropriate `-upgrade` flag will be run automatically.

After this, you must still run `terraform apply` to apply the changes to your infrastructure.

Run `terraform plan` first to preview what will change. To keep a copy for review, redirect the output to a dated file:

```shell
terraform plan -no-color > "$(date +%Y%m%d-%H%M%S)-upgrade-plan.txt" 2>&1
```

The `./upgrade-terraform-modules` script prints an exact capture command when it finishes. Consider sharing the saved plan with Worklytics support, teammates, or an LLM before you apply.

## Reviewing your Terraform plan

Before running `terraform apply`, review your plan output for changes that are difficult or impossible to undo without operational work outside Terraform.

### High-risk changes to watch for

**Rotating or destroying the pseudonymization SALT value/secret**

Any data processed with the prior SALT value will be inconsistent with data processed after the change (pseudonyms for the same identifier will differ). You must either restore the prior SALT value or re-ingest all affected data to Worklytics.

In Terraform plans, look for `random_password.pseudonym_salt` (or equivalent secret resources) being destroyed/replaced, or SSM parameters / Secret Manager secrets holding `PSOXY_SALT` being recreated.

**Replacing Lambda or Cloud Function resources — especially their function URLs**

Replacing proxy compute changes the endpoint Worklytics calls for API-mode connectors. Update the corresponding connections in Worklytics with the new function URL(s) after apply.

**Replacing any `-input` buckets**

Bulk connectors receive files via `-input` buckets. If Terraform replaces these buckets, update any data pipelines, export jobs, or manual upload processes that write files into them.

**Replacing any `-sanitized` buckets**

Worklytics reads sanitized bulk output from these buckets. If they are replaced, update the corresponding connections in Worklytics (bucket name, path, and any IAM principal used for access).

**Replacing parameters/secrets that hold API credentials and are NOT managed by this Terraform configuration**

Some deployments store third-party API keys in SSM, Secrets Manager, or GCP Secret Manager outside the modules Terraform manages. If a plan destroys or replaces those resources, you must recover the original credential values (from backup or your secrets store) or obtain new credentials from the data source and update both Terraform and the live secret before apply completes.

**Replacing the IAM role used by Worklytics to invoke cloud functions or read from `-sanitized` buckets**

Worklytics connections reference the principal that calls your proxy (function URL invoker role, or role/user that reads sanitized buckets). If that role is replaced, update the corresponding connections in Worklytics.

### Getting help reviewing the plan

After saving your plan to a file, share it with Worklytics support, a teammate, or an LLM to help scan for the issues above. Example prompt for an LLM:

```text
Review and summarize the output of terraform plan stored in 20260618-143022-upgrade-plan.txt.

Flag any high-risk changes, especially:
- destruction or replacement of the pseudonymization SALT/secret
- replacement of Lambda/Cloud Function resources (and their function URLs)
- replacement of any -input buckets
- replacement of any -sanitized buckets
- replacement of parameters/secrets holding API credentials that are NOT managed by this Terraform configuration
- replacement of the IAM role used by Worklytics to invoke cloud functions or read from -sanitized buckets

For each issue found, explain the operational impact and what I must do before applying this plan.
```

Replace the filename with your actual plan file. Do not paste live secrets or credentials into third-party tools; the plan file itself should not contain secret values if Terraform is configured correctly, but review your organization's policies before sharing plan output externally.

## Legacy Deployments (Initial version pre-`v0.4.30`)
If you initially used one of our examples prior to `v0.4.30`, or did not use one of our examples, you will need to manually update the version references in your configuration.


Open each `.tf` file in the root of your configuration. Find all module references ending in a version number, and update them to the new version.

Eg, look for something like the following:

```hcl
module "psoxy" {
  source = "github.com/Worklytics/psoxy//terraform?ref=v0.4.37"
}
```

update the `v0.4.37` to `v0.4.62`:

```hcl
module "psoxy" {
  source = "github.com/Worklytics/psoxy//terraform?ref=v0.4.62"
}
```

Then run `terraform init` after saving the file to download the new version of each module(s).
