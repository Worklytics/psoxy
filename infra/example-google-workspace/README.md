# example-google-workspace

A Terraform root module to provision GCP project for Psoxy, configure it, and create necessary infra
to for connections to all supported Google Workspace sources, with state stored to local filesystem.
As such, it is not appropriate for scenario with multiple developers. As state will contain
sensitive information (eg, service account keys), care should be taken in production to ensure that
the filesystem in question is secure or another Terraform backend should be used (eg, GCS bucket
encrypted with a CMEK).

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
```

for example:
```terraform
billing_account_id   = "0A2AE4-1D396E-1219D9"
folder_id            = "33576234038"
project_id           = "psoxy-dev-alice"
worklytics_sa_emails = [
  "worklytics-3cD92f@worklytics-eu.iam.gserviceaccount.com"
]
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

