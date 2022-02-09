# aws-hris

This example provisions psoxy as an AWS lambda that pseudonomizies HRIS files 

## Authentication

### AWS
Follow [`docs/aws/getting-started.md`](../../../docs/aws/getting-started.md) to setup AWS CLI to
authenticate as a user/role which can access/create the AWS account in which you wish to provision
your psoxy instance.

## Example Configuration

Example `terraform.tfvars`:
```terraform
aws_account_id                = "123456789"
aws_assume_role_arn           = "arn:aws:iam::123456789:role/InfraAdmin"
environment_name              = "dev-aws"
bucket_prefix                 = "some_prefix_for_bucket"
caller_aws_account_id         = "914358739851:root"
caller_external_user_id       = "your-worklytics-service-account"
```

