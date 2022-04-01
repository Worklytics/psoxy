# AWS

Example Terraform configuration for deploying psoxy in AWS for Slack and/or Zoom.

Example `terraform.tfvars`:
```terraform
aws_account_id                = "123456789" # ID of AWS account into which to deploy psoxy infra
aws_assume_role_arn           = "arn:aws:iam::123456789:role/InfraAdmin" # role within your AWS account which can manage infra
caller_aws_account_id         = "939846301470:root" # Worklytics' AWS account ID
caller_external_user_id       = "your-worklytics-service-account-id" # obtained from Worklytics
```

`caller_external_user_id` is a SA provided by Worklytics that uniquely identifies your tenant in
Worklytics' premises.

