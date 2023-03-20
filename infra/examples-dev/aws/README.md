# aws

This example deploys Psoxy in AWS.

## Getting Started

## Prereqs

Ensure you have all the [prerequisites](https://github.com/Worklytics/psoxy/blob/main/README.md#prerequisites)
installed.

## Authenticate your Environment

In the location where you plan to execute this terraform example:

  1. Authenticate the AWS CLI as a user/role which can access/create the AWS account in which you wish
     to provision your psoxy instance (see [docs/aws/getting-started.md](https://github.com/Worklytics/psoxy/blob/main/docs/aws/getting-started.md#prerequisites)
  2. If connecting to Microsoft 365 data sources, authenticate the Azure CLI (run `./az-login` in
     this directory)
  3. If connecting to Google Workspace data sources, authenticate the Google Cloud SDK (run
     `gcloud auth login`)

## Customize your Configuration
  1. open `main.tf`
  2. Remove any providers you don't need.
     - if you don't need Google Workspace data sources, remove the `google` provider blocks
     - if you don't need Microsoft 365 data sources, remove the `azuread` provider blocks
  3. Set a secure Terraform backend (eg, not `local` if you intend this configuration for production
     use) (see https://github.com/Worklytics/psoxy/blob/main/docs/aws/getting-started.md#terraform-state-backend)

## Initialize your Configuration
  1. run `./init-example` to initialize your Terraform configuration and generate a `terraform.tfvars` file
  2. open `terraform.tfvars`, customize it as needed.

## Deploy your Configuration
  1. run `terraform apply`
  2. review the plan and type `yes` to deploy


## Production Use

In order to get the Service Account Unique ID value for `caller_gcp_service_account_ids`, log in to
your Worklytics Account and visit the [Configuration Values](https://app.worklytics.co/analytics/integrations/configuration) page.


