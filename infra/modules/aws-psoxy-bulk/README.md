# aws-hris

This example provisions an AWS Lambda that pseudonomizes files uploaded into the bucket. The source file is dropped in a *input bucket* and
result will appear in *output bucket*.

## Authentication

### AWS
Follow [`docs/aws/getting-started.md`](../../../docs/aws/getting-started.md) to setup AWS CLI to
authenticate as a user/role which can access/create the AWS account in which you wish to provision
your psoxy instance.

## Configuration

Example `terraform.tfvars`:
```terraform
aws_account_id                = "123456789"
aws_assume_role_arn           = "arn:aws:iam::123456789:role/InfraAdmin"
environment_name              = "dev-aws"
bucket_prefix                 = "some_prefix_for_bucket"
caller_aws_account_id         = "914358739851:root"
caller_external_user_id       = "your-worklytics-service-account-id"
source_kind                   = "hris"
```

It is mandatory that `source_kind` matches with a configuration file provided into the platform. For example, if the
value is `hris` it will expect a `hris.yaml` file at some point. You could include this kind of files as part of `config`
folder or include it as part of the deployment files in the target folder.

Example of `hris.yaml` config file with Base64 rules:

```yaml
SOURCE: hris-import
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

And then converted to Base64 as [Custom Rules](../../../docs/custom-rules.md) documentation explains

