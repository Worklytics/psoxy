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
```

Example of `hris.yaml` config file with Base64 rules:

```yaml
SOURCE: aws-hris-import
RULES: cHNldWRvbnltaXphdGlvbnM6CiAgLSBjc3ZDb2x1bW5zOgogICAgICAtICJlbWFpbCIKcmVkYWN0aW9uczoKICAtIGNzdkNvbHVtbnM6CiAgICAgIC0gIm1hbmFnZXJFbWFpbCI=
```

In this case rules are created based on following configuration:

```yaml
pseudonymizations:
  - csvColumns:
      - "email"
redactions:
  - csvColumns:
      - "managerEmail"
```

And then converted to Base64 as [Custom Rules] documentation explains

