# example-google-workspace

**DEPRECATED - will remove in v0.5**

A Terraform root module to provision GCP project for Psoxy, configure it, and create necessary infra
to for connections to all supported Google Workspace sources, with state stored to local filesystem.
As such, it is not appropriate for scenario with multiple developers. As state will contain
sensitive information (eg, service account keys), care should be taken in production to ensure that
the filesystem in question is secure or another Terraform backend should be used (eg, GCS bucket
encrypted with a CMEK).

## Usage

Create a file in this directory named `terraform.tfvars` to specify your settings:

```terraform
gcp_project_id                = "psoxy-dev-aws-example-12314332"
gcp_org_id                    = "123456789" # your GCP organization ID; if existing project, you can leave as empty string and see the value from `terraform plan`
gcp_folder_id                 = "111111111111" # folder ID for the project; if existing project, you can leave as empty string and see the value from `terraform plan`
gcp_billing_account_id        = "123456-789ABC-DEF012" # GCP billing account ID for project; if existing project, you can leave as empty string and see the value from `terraform plan`
environment_name     = "--OPTIONAL helpful name to identify your environment --"
worklytics_sa_emails = [
  "--email address of service account that personifies your Worklytics account--"
]
psoxy_base_dir                = "/home/user/psoxy/" # use absolute path to your local psoxy repo****
```

In order to get the Service Account Email value for `worklytics_sa_emails`, log in to your Worklytics
Account and visit the [Configuration Values](https://app.worklytics.co/analytics/integrations/configuration) page.

For example:
```terraform
gcp_project_id                = "psoxy-dev-alice"
gcp_org_id                    = "123456789" # your GCP organization ID; if existing project, you can leave as empty string and see the value from `terraform plan`
gcp_folder_id                 = "33576234038" # folder ID for the project; if existing project, you can leave as empty string and see the value from `terraform plan`
gcp_billing_account_id        = "0A2AE4-1D396E-1219D9" # GCP billing account ID for project; if existing project, you can leave as empty string and see the value from `terraform plan`
worklytics_sa_emails = [
  "worklytics-3cD92f@worklytics-eu.iam.gserviceaccount.com"
]
psoxy_base_dir                = "/home/user/psoxy/" # use absolute path to your local psoxy repo
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

## Notes

Deployment will create:
  - one GCS bucket for staging artifacts
  - two GCS buckets per 'bulk' connector (eg, sanitizer to process flat files); one for input fils, and one for output
