# gcp-hris

This example provisions psoxy as an AWS lambda that connects to Microsoft 365 data sources.

A Terraform root module to provision GCP project for Psoxy, configure it, and create necessary infra
to upload files based on HRIS source kind in a bucket and drop the modified file from Psoxy in an output bucket that can be used
to read it from Worklytics.

Deployment will create three buckets: one for deploying the cloud function and the ones for import/processed.
When a file is uploaded into the `-input` bucket the cloud function is triggered and it will apply the Psoxy rules
defined in the file. The result of that process will be dropped in the `-output` bucket in the same path that it
was in the original path from `-input` bucket

## Usage

Create a file in this directory named `terraform.tfvars` to specify your settings:

```terraform
gcp_project_id                = "psoxy-dev-aws-example-12314332"
gcp_org_id                    = "123456789" # your GCP organization ID; if existing project, you can leave as empty string and see the value from `terraform plan`
gcp_folder_id                 = "111111111111" # folder ID for the project; if existing project, you can leave as empty string and see the value from `terraform plan`
gcp_billing_account_id        = "123456-789ABC-DEF012" # GCP billing account ID for project; if existing project, you can leave as empty string and see the value from `terraform plan`
environment_name              = "--OPTIONAL helpful name to identify your environment --"
worklytics_sa_emails          = [
  "--email address of service account that personifies your Worklytics account--"
]
region               = "--OPTIONAL region where the cloud function will be deployed"
bucket_prefix        = "Name of the buckets to create; a suffix will be added later as part of the deployment process"
bucket_location      = "--OPTIONAL location where the buckets will be created"
source_kind          = "hris"
```

for example:
```terraform
gcp_billing_account_id   = "0A2AE4-1D396E-1219D9"
gcp_folder_id            = "33576234038"
gcp_project_id           = "psoxy-dev-alice"
worklytics_sa_emails = [
  "worklytics-3cD92f@worklytics-eu.iam.gserviceaccount.com"
]
bucket_prefix        = "alice-psoxy-dev"
source_kind          = "hris"
```

You could check more details about configuration in the [module documentation](../../modules/gcp-bulk/readme.md)

## Deployment

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

## Cleanup

Execute and confirm (be careful, all the files uploaded in both input and output will be removed)
```shell
terraform apply -destroy
```

