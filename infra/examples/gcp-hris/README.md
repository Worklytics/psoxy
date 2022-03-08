# example-gcp-hris

A Terraform root module to provision GCP project for Psoxy, configure it, and create necessary infra
to upload HRIS files in a bucket and drop the modified file from Psoxy in an output bucket that can be used
to read it from Worklytics.

Deployment will create three buckets: one for deploying the cloud function and the ones for import/processed.
When a file is uploaded into the `-import` bucket the cloud function is triggered and it will apply the Psoxy rules
defined in the file. The result of that process will be dropped in the `-processed` bucket in the same path that it
was in the original path from `-import` bucket

## Usage

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
```

for example:
```terraform
billing_account_id   = "0A2AE4-1D396E-1219D9"
folder_id            = "33576234038"
project_id           = "psoxy-dev-alice"
worklytics_sa_emails = [
  "worklytics-3cD92f@worklytics-eu.iam.gserviceaccount.com"
]
bucket_prefix        = "alice-psoxy-dev"
```

Initialize your configuration (at this location in directory hierarchy):
```shell
terraform init
```

If you're using an existing GCP project rather than creating one, you'll need to import it to
terraform first. For example:
```shell
terraform import google_project.psoxy-project --your-psoxy-project-id--
```

Apply
```shell
terraform apply
```

Review the plan and confirm to apply.