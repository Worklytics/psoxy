# dev-personal

A Terraform root module to create a personal development environment for Psoxy, with state stored to
local filesystem. As such, it is not appropriate for scenario with multiple developers or production
use.

It can serve as guide for  how to create equivalent Terraform configurations for production use
(with state in the cloud, encrypted GCS bucket/etc; and `.tfvars`/etc files under version control).

## Usage

Create a file in this directory named `terraform.tfvars` to specify your personal settings, with
content

```terraform
billing_account_id = "--your billing account id--"
project_id         = "--desired project id (must be unique)--"
folder_id           = "--numeric id of GCP folder in which to put the project--"
environment_name    = "--helpful name to identify your environment--"
worklytics_sa_email = "--email address of service account that personifies your Worklytics account--"
```

for example, Erik's personal env would appear as follows:
```terraform
billing_account_id  = "002DF4-4D356E-2123F9"
folder_id           = "335768754038"          # our devel folder
environment_name    = "erik"
project_id          = "psoxy-dev-erik"
worklytics_sa_email = "worklytics-dev-local-shared@worklytics-staging.iam.gserviceaccount.com"
```

Initialize your configuration (at this location in directory hierarchy):
```shell
terraform init
```
If you're using an existing GCP project, you'll need to import it to terraform first.

Apply
```shell
terraform apply
```


Review the plan, and confirm to apply.

