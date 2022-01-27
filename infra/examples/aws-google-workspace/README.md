# AWS

Example Terraform configuration for deploying psoxy in AWS and connecting to Google Workspace sources

Example `terraform.tfvars`:
```terraform
aws_account_id                = "123456789"
aws_assume_role_arn           = "arn:aws:iam::123456789:role/InfraAdmin"
caller_aws_account_id         = "987654321:root"
caller_external_user_id       = "erik@worklytics.co"
gcp_project_id                = "psoxy-dev-aws-example-12314332"
environment_name              = "dev-aws"
gcp_folder_id                 = "111111111111"
gcp_billing_account_id        = "123456-789ABC-DEF012"
connector_display_name_suffix = " Psoxy Dev AWS - erik"
```

