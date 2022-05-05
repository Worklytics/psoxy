# aws-hris

This example provisions an AWS Lambda that pseudonomizes HRIS files. The source file is dropped in a *input bucket* and
result will appear in *output bucket*.

## Authentication

### AWS
Follow [`docs/aws/getting-started.md`](../../../docs/aws/getting-started.md) to setup AWS CLI to
authenticate as a user/role which can access/create the AWS account in which you wish to provision
your psoxy instance.

## Example Configuration

Example `terraform.tfvars`:
```terraform
aws_account_id                = "123456789" # your AWS account ID
aws_assume_role_arn           = "arn:aws:iam::123456789:role/InfraAdmin" # sufficiently privileged role within your AWS account to provision necessary infra
caller_aws_account_id         = "914358739851:root" # for production use, this should be Worklytics' AWS account; for testing, it can be your own
caller_external_user_id       = "123456712345671234567" # 21-digit numeric string you should obtain from Worklytics
instance_id                   = "hris-function"
source_kind                   = "hris"
```

You could check more details about configuration in the [module documentation](../../modules/aws-bulk/readme.md)

## Deployment

Initialize your configuration (at this location in directory hierarchy):
```shell
terraform init
```

Apply
```shell
terraform apply
```

Review the plan and confirm to apply.

## Cleanup

Execute and confirm (be careful, all the files uploaded in both input and output will be removed)
```shell
terraform apply -destroy
```

As default, Secret Manager Stores is marked by "soft" deleted and it will be queued to be removed in a week. In case
you want to create the same infra without removing it you will probably receive a conflict error. Please go to
the AWS console and run the following command:

```shell
aws secretsmanager delete-secret --secret-id PSOXY_SALT --force-delete-without-recovery
```

