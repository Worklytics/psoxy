# example-google-workspace

A Terraform root module to provision GCP project for Psoxy, configure it, and create necessary infra
to for connections to all supported Google Workspace sources, with state stored to local filesystem.
As such, it is not appropriate for scenario with multiple developers. As state will contain
sensitive information (eg, service account keys), care should be taken in production to ensure that
the filesystem in question is secure or another Terraform backend should be used (eg, GCS bucket
encrypted with a CMEK).

## Usage

Run `./init` in this directory to create an example `terraform.tfvars` file.  Edit it to customize.

Create a file in this directory named `terraform.tfvars` to specify your settings:

Initialize your configuration (at this location in directory hierarchy):
```shell
terraform init
```

Apply
```shell
terraform apply
```

Review the plan and confirm to apply.

## Notes

Deployment will create:
  - one GCS bucket for staging artifacts
  - two GCS buckets per 'bulk' connector (eg, sanitizer to process flat files); one for input files,
    and one for output
