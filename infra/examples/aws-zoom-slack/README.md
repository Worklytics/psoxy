# AWS

Example Terraform configuration for deploying psoxy in AWS for Slack and/or Zoom.

Example `terraform.tfvars`:
```terraform
aws_account_id                = "123456789" # your AWS account ID
aws_assume_role_arn           = "arn:aws:iam::123456789:role/InfraAdmin" # sufficiently privileged role within your AWS account to provision necessary infra
caller_aws_account_id         = "914358739851:root" # for production use, this should be Worklytics' AWS account; for testing, it can be your own
caller_external_user_id       = "123456712345671234567" # 21-digit numeric string you should obtain from Worklytics
```

`caller_external_user_id` is a SA provided by Worklytics that uniquely identifies your tenant in
Worklytics' premises.

