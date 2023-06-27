# Encryption Keys in AWS

As of June 2023, the following resources provisioned by Psoxy modules support use of CMEKs:
  - Lambda function environment variables
  - SSM Parameters
  - Cloud Watch Log Groups

## Pre-existing Key
The `psoxy-example-aws` example provides a `project_aws_key_arn` variable, that, if provided, will
be set as the encryption key for these resources. A few caveats:
  - The AWS principal your Terraform is running as must have permissions to encrypt/decrypt with the
    key (it needs to be able to read/write the lambda env, ssm params, etc)
  - The key should be in the same AWS region you're deploying to.

## Provisioning a Key

```hcl

resource "aws_kms_key" "key" {
  description             = "KMS key for Psoxy"
  enable_key_rotation     = true
  is_enabled              = true
}

# then replace all use of `var.project_aws_key_arn` with `aws_kms_key.key.arn` in your `main.tf`
```

Be sure to allow Cloud Watch to use it, as described in [AWS CloudWatch docs](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/encrypt-log-data-kms.html)

A key policy like the following should work, but please adapt to your environment and scope as needed
to follow your security policies, such as principle of least privilege. In particular, note that the
first statement must be set to restrict who can manage the CMEK, without locking out your Terraform's
AWS principal.

```hcl
resource "aws_kms_key_policy" "key_policy_including_cloudwatch" {
    key_id = var.project_aws_kms_key_arn
    policy = jsonencode(
        {
            "Version" : "2012-10-17",
            "Id" : "key-default-1",
            "Statement" : [
                {
                    "Sid": "Allow IAM Users to Manage Key",
                    "Effect": "Allow",
                    "Principal": {
                        "AWS": "arn:aws:iam::${var.aws_account_id}:root"
                    },
                    "Action": "kms:*",
                    "Resource": "*"
                },
                {
                    "Effect" : "Allow",
                    "Principal" : {
                        "Service" : "logs.${var.aws_region}.amazonaws.com"
                    },
                    "Action" : [
                        "kms:Encrypt",
                        "kms:Decrypt",
                        "kms:ReEncrypt",
                        "kms:GenerateDataKey",
                        "kms:Describe"
                    ],
                    "Resource" : "*"
                }
            ]
        })
}
```


## More options

If you need more granular control of CMEK by resource type, review the `main.tf` and variables
exposed by the `aws-host` module for some options.
