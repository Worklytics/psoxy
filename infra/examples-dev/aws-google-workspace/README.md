# AWS

Example Terraform configuration for deploying psoxy in AWS and connecting to Google Workspace sources

Example `terraform.tfvars`:
```terraform
aws_account_id                = "123456789" # your AWS account ID
aws_assume_role_arn           = "arn:aws:iam::123456789:role/InfraAdmin" # sufficiently privileged role within your AWS account to provision necessary infra
caller_aws_arns = [
  "arn:aws:iam::914358739851:root" # for production use, this should be Worklytics' AWS account; for testing, it can be your own AWS account
]
caller_gcp_service_account_ids = [
  "123456712345671234567" # 21-digit numeric string you should obtain from Worklytics
]

gcp_project_id                = "psoxy-dev-aws-example-12314332"
psoxy_base_dir                = "~/psoxy/" # TODO: we suggest using absolute path here
```

In order to get the Service Account Unique ID value for `caller_gcp_service_account_ids`, log in to your Worklytics
Account and visit the [Configuration Values](https://app.worklytics.co/analytics/integrations/configuration) page.


