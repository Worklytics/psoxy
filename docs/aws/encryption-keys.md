# Encryption Keys in AWS

As of June 2023, the following resources provisioned by Psoxy modules support use of CMEKs:

- Lambda function environment variables
- SSM Parameters
- Cloud Watch Log Groups
- S3 Buckets

## Pre-existing Key

The `psoxy-example-aws` example provides a `project_aws_key_arn` variable, that, if provided, will be set as the encryption key for these resources. A few caveats:

- The AWS principal your Terraform is running as must have permissions to encrypt/decrypt with the key (it needs to be able to read/write the lambda env, ssm params, etc)
- The key should be in the same AWS region you're deploying to.
- CloudWatch must be able to use the key, as described in [AWS CloudWatch docs](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/encrypt-log-data-kms.html)

In `example-dev/aws/kms-cmek.tf`, we provide a bunch of lines that you can uncomment to use encryption on S3 and properly set key policy to support S3/CloudWatch use.

For production use, you should adapt the key policy to your environment and scope as needed to follow your security policies, such as principle of least privilege.

## Provisioning a Key

```hcl
resource "aws_kms_key" "key" {
  description             = "KMS key for Psoxy"
  enable_key_rotation     = true
  is_enabled              = true
}

# then replace all use of `var.project_aws_key_arn` with `aws_kms_key.key.arn` in your `main.tf`
```

## More options

If you need more granular control of CMEK by resource type, review the `main.tf` and variables exposed by the `aws-host` module for some options.
