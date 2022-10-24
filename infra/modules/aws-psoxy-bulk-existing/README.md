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

It is mandatory that `source_kind` matches with a configuration file provided into the platform.
For example, if the  value is `hris` it will expect a `hris.yaml` file located in `configs/`
directory at the root of your checkout.

To create this configuration:
  1. Create a file named `rules.yaml`.
  2. File it with YAML rules, consisting of lists of columns to redact and pseudonymize. For example:
```yaml
columnsToRedact:
- "SOMETHING_SENSITIVE"
columnsToPseudonymize:
- "EMPLOYEE_ID"
- "EMPLOYEE_EMAIL"
- "MANAGER_ID"
- "MANAGER_EMAIL"
```
  3. Base 64 encode it `cat rules.yaml | base64`
  4. Use value from (3) as the value of `RULES` in `hris.yaml` file, structure shown below:
```yaml
SOURCE: hris-import
RULES: aW5mb2Fkc0BzdW4uY29tLmJ1aWxkLmJ1aWxkLmNvbQ==
```

