# AWS

Example Terraform configuration for deploying psoxy in AWS for Slack and/or Zoom.

Example `terraform.tfvars`:
```terraform
aws_account_id                = "123456789"
aws_assume_role_arn           = "arn:aws:iam::123456789:role/InfraAdmin"
caller_aws_account_id         = "987654321:root"
caller_external_user_id       = "your-worklytics-service-account-id"
environment_name              = "dev-aws"
```

`caller_external_user_id` is a SA provided by Worklytics that uniquely identifies your tenant in
Worklytics' premises.

