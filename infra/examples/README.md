# Example Terraform Configurations

This directory provides various examples of creation of Proxy instances to support various platforms
and data sources.

We recommend you choose the best one for your use case, copy it, and modify it as needed:
```shell
cp -r aws-msft-365 acme-com
```

Delete the `.gitignore` file, so state/etc can be committed to git if you desire:

```shell
rm acme-com/.gitignore
```

Files specific to your Terraform configuration (such as `terraform.tfvars`, `.terraform/`, and, if
you opt for [local state](https://www.terraform.io/language/settings/backends/local), your
`terraform.tfstate*` files) will be under version control.

Professional services to assist in building a Terraform configuration appropriate for your needs
is available from Worklytics. Please contact [sales@worklytics.co](mailto:sales@worklytics.co).

## Dependencies

These example configurations reference Psoxy-provided Terraform modules as dependencies via their
remote addresses hosted in GitHub. If you prefer to modify any of these modules, you can convert
these to local references throughout your configuration:
  - replacing `git::https://github.com/worklytics/psoxy//infra/modules` with `../../modules`
  - any `ref` parameter, such as `?ref=v0.1.0-beta.1`, should be removed from the module `source`
    attributes

Generally, we omit `ref` parameters for simplicity - so implicitly dependencies are bound to their
'latest' version. To pin to a specific release, such as `v0.1.0-beta.1`, append `?ref=v0.1.0-beta.1`
to the modules `source`, such as:

```terraform
module "psoxy-aws" {
  source = "git::https://github.com/worklytics/psoxy//infra/modules/aws"

  caller_aws_account_id   = var.caller_aws_account_id
  caller_external_user_id = var.caller_external_user_id
  aws_account_id          = var.aws_account_id
}
```

## Terraform State

These configurations persist their state in the local file system by default.  For production use,
we suggest you change the Terraform configuration to use a remote backend, as some values serialized
into your Terraform state should be handled as sensitive data. This also faciliates shared
administration of the resulting infrastructure (eg, multiple users can execute `terraform apply`,
with concurrency controlled by terraform locking the remote state).

Local state example:
```terraform
  backend "local" {
  }
```

Remote state example:
```terraform
backend "s3" {
  bucket = "terraform-state-bucket"
  key    = "terraform.tfstate"
  region = "us-east-1"
}
```

## Disclaimer
All examples in this directory and its subdirectories are provided as-is, for reference only, with
no warranty. You should review all resulting Terraform plans carefully before applying them to your
environment. Additionally, we strongly recommend that you make example curl requests to any proxy
instance you deploy to verify you are satisified with the behavior of the proxy with regard to the
transformation of your data.


