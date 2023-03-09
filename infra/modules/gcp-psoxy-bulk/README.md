# example-gcp-hris

A Terraform root module to provision GCP project for Psoxy, configure it, and create necessary infra
to upload files in a bucket and drop the modified file from Psoxy in an output bucket that can be used
to read it from Worklytics.

Deployment will create three buckets: one for deploying the cloud function and the ones for import/processed.
When a file is uploaded into the `-input` bucket the cloud function is triggered and it will apply the Psoxy rules
defined in the file. The result of that process will be dropped in the `-output` bucket in the same path that it
was in the original path from `-input` bucket

## Configuration

Create a file in this directory named `terraform.tfvars` to specify your settings:

```terraform
billing_account_id   = "--your billing account id--"
project_id           = "--desired project id (must be unique)--"
folder_id            = "--numeric id of GCP folder in which to put the project--"
environment_name     = "--OPTIONAL helpful name to identify your environment --"
worklytics_sa_emails = [
  "--email address of service account that personifies your Worklytics account--"
]
region               = "--OPTIONAL region where the cloud function will be deployed"
bucket_prefix        = "Name of the buckets to create; a suffix will be added later as part of the deployment process"
bucket_location      = "--OPTIONAL location where the buckets will be created"
source_kind          = "Kind of the content to process; it should match one of the config.yaml file available"
```

In order to get the Service Account Email value for `worklytics_sa_emails`, log in to your Worklytics
Account and visit the [Configuration Values](https://app.worklytics.co/analytics/integrations/configuration) page.

For example:
```terraform
billing_account_id   = "0A2AE4-1D396E-1219D9"
folder_id            = "33576234038"
project_id           = "psoxy-dev-alice"
worklytics_sa_emails = [
  "worklytics-3cD92f@worklytics-eu.iam.gserviceaccount.com"
]
bucket_prefix        = "alice-psoxy-dev"
source_kind          = "hris"
```

It is mandatory that `source_kind` matches with a configuration file provided into the platform. For example, if the
value is `hris` it will expect a `hris.yaml` file at some point. You could include this kind of files as part of `config`
folder or include it as part of the deployment files in the target folder.

Example of `hris.yaml` config file with Base64 rules:

```yaml
SOURCE: hris-import
RULES: cHNldWRvbnltaXphdGlvbnM6CiAgLSBjc3ZDb2x1bW5zOgogICAgICAtICJlbWFpbCIKcmVkYWN0aW9uczoKICAtIGNzdkNvbHVtbnM6CiAgICAgIC0gIm1hbmFnZXJFbWFpbCI=
```

In this case rules are created based on following configuration:

```yaml
pseudonymizations:
  - csvColumns:
      - "email"
redactions:
  - csvColumns:
      - "managerEmail"
```

And then converted to Base64 as [Custom Rules](../../../docs/custom-rules.md) documentation explains
