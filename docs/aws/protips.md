# Protips

Some ideas on how to support scenarios and configuration requirements beyond what our default
examples show:

## Encryption Keys

see [encryption-keys.md](encryption-keys.md)

## Tagging ALL infra created by your Terraform Configuration

If you're using our AWS example, it should support a `default_tags` variable.

You can add the following in your `terrform.tfvars` file to set tags on all resources created by the
example configuration:

```hcl
default_tags = {
  Vendor = "Worklytics"
}
```

If you're not using our AWS example, you can add the following to your configuration, then you will
need to modify the `aws` provider block in your configuration to add a `default_tags`. Example shown
below:

See: [https://registry.terraform.io/providers/hashicorp/aws/latest/docs#default_tags]

```hcl
provider "aws" {
  region = var.aws_region

  assume_role {
    role_arn = var.aws_assume_role_arn
  }

  default_tags {
    Vendor  = "Worklytics"
  }

  allowed_account_ids = [
    var.aws_account_id
  ]
}
```

## Extensibility

To support extensibility, our Terraform examples/modules output the IDs/names of the major resources
they create, so that you can compose them with other Terraform resources.

### Buckets

The `aws-host` module outputs `bulk_connector_instances`; a map of `id => instance` for each bulk
connector. Each of these has two attributes that correspond to the names of its related buckets:

- `sanitized_bucket_name`
- `input_bucket_name`

So in our AWS example, you can use these to enable logging, for example, you could do something like
this: (YMMV, syntax etc should be tested)

```hcl
locals {
    # Gather buckets created by the various terraform modules
    buckets_to_secure = concat(
        flatten([ for k, instance in module.psoxy.bulk_connector_instances : [ instance.sanitized_bucket, instance.input_bucket ] ]),
        values(module.psoxy.lookup_output_buckets)[*]
    )

    id_of_bucket_to_store_logs = "{YOUR_BUCKET_ID_HERE}"
}

resource "aws_s3_bucket_logging" "logging" {
  for_each = toset(local.buckets_to_secure)

  bucket = each.value.sanitized_bucket_name

  target_bucket = local.id_of_bucket_to_store_logs
  target_prefix = "psoxy/${each.key}/"
}
```

You can also set bucket-level policies to restrict access to SSL-only, with something like the
following:

```hcl
locals {
  buckets_to_secure = concat(
    flatten([ for k, instance in module.psoxy.bulk_connector_instances : [ instance.sanitized_bucket, instance.input_bucket ] ]),
    values(module.psoxy.lookup_output_buckets)[*]
  )
}

resource "aws_s3_bucket_policy" "deny_s3_nonsecure_transport" {
  for_each = toset(local.buckets_to_secure)

  bucket = each.key
  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [
      {
        Sid      = "DenyNonSecureTransport"
        Effect   = "Deny"
        Action   = ["s3:*"]
        Principal = "*"
        Resource =  [
          "arn:aws:s3:::${each.key}",
          "arn:aws:s3:::${each.key}/*"
        ]
        Condition = {
          Bool = {
            "aws:SecureTransport" = false
          }
        }
      }
    ]
  })
}
```

Analogous approaches can be used to configure versioning, replication, etc;

- [`aws_s3_bucket_versioning`](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket_versioning)
- [`aws_s3_bucket_replication`](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket_replication)

Note that encryption, lifecycle, public_access_block are set by the Workltyics-provided modules, so
you may have conflicts issues if you also try to set those outside.

## Lambda Execution Role
*beta* - released from v0.4.50; YMMV, and may be subject to change.

The terraform modules we provide provision execution roles for each lambda function, and attach
by default attach the appropriate AWS Managed Policy to each.

Specifically, this is [`AWSLambdaBasicExecutionRole`](https://docs.aws.amazon.com/aws-managed-policy/latest/reference/AWSLambdaBasicExecutionRole.html),
unless you're using a VPC - in which case it is `AWSLambdaVPCAccessExecutionRole`(https://docs.aws.amazon.com/aws-managed-policy/latest/reference/AWSLambdaVPCAccessExecutionRole.html).

For organizations that don't allow use of AWS Managed Policies, you can use the
`aws_lambda_execution_role_policy_arn` variable to pass in an alternative which will be used INSTEAD
of the AWS Managed Policy.

