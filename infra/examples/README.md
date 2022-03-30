# Example Terraform Configurations

This directory provides various examples of creation of Proxy instances to support various platforms
and data sources.

Before adapting one of these examples for your use-case, we recommend that you create a private fork
of this repository, as described in [../../README.md#Setup](README.md#Setup).

Then you should modify (or delete) the `.gitignore` file in this directory, so that configuration
files specific to your Terraform configuration (such as `terraform.tfvars`, `.terraform/`, and, if
you opt for [local state](https://www.terraform.io/language/settings/backends/local), your
`terraform.tfstate*` files) will be under version control.

NOTE: for production use, we suggest you change the Terraform configuration to use a remote backend
rather than your local file system, as some values serialized into your Terraform state should be
handled as sensitive data.

Professional services to assist in building a Terraform configuration appropriate for your needs
is available from Worklytics. Please contact [sales@worklytics.co](mailto:sales@worklytics.co).




