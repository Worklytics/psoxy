# Protips

Some ideas on how to support scenarios and configuration requirements beyond what our default examples show:

## Encryption Keys

see [encryption-keys.md](encryption-keys.md)

## Tagging ALL infra created by your Terraform Configuration

If you're using our AWS example, it should support a `default_tags` variable.

You can add the following in your `terrform.tfvars` file to set tags on all resources created by the example configuration:

```hcl
default_tags = {
  Vendor = "Worklytics"
}
```

If you're not using our AWS example, you can add the following to your configuration, then you will need to modify the `aws` provider block in your configuration to add a `default_tags`. Example shown below:

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

To support extensibility, our Terraform examples/modules output the IDs/names of the major resources they create, so that you can compose them with other Terraform resources.

### Buckets

The `aws-host` module outputs `bulk_connector_instances`; a map of `id => instance` for each bulk connector. Each of these has two attributes that correspond to the names of its related buckets:

- `sanitized_bucket_name`
- `input_bucket_name`

So in our AWS example, you can use these to enable logging, for example, you could do something like this: (YMMV, syntax etc should be tested)

See `s3-extra-sec.tf` in example repo from v0.4.58+ for example code you can uncomment and modify.

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

You can also set bucket-level policies to restrict access to SSL-only, with something like the following:

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

Note that encryption and public_access_block are set by the Worklytics-provided modules, so you may have conflict issues if you also try to set those outside.

### Lifecycle Configurations

S3 bucket lifecycle configurations are provisioned inside the Worklytics modules by default. However, AWS only allows one lifecycle configuration resource to be applied to a bucket at a time.

If you need to define custom or additional lifecycle rules for all buckets (e.g., to abort incomplete multipart uploads or transition old objects), you can provision your own `aws_s3_bucket_lifecycle_configuration` resources.

#### 1. Avoid Conflicts with Module-Level Rules
The Worklytics modules currently manage S3 lifecycle configuration for these buckets. Because AWS allows only one `aws_s3_bucket_lifecycle_configuration` per bucket, you cannot safely add a separate lifecycle configuration resource for the same bucket unless the module is changed to stop managing lifecycle rules for it.

Do **not** rely on setting `bulk_input_expiration_days` or `bulk_sanitized_expiration_days` to `0` as a way to disable the module-managed lifecycle configuration. That is not a currently supported mechanism.

If you need fully custom lifecycle rules on these buckets, use a version of the module that does not create lifecycle configuration for them, or update/fork the module to make that behavior conditional before adding your own `aws_s3_bucket_lifecycle_configuration` resources.

#### 2. Exclude the Artifacts Bucket
> [!WARNING]
> Do **NOT** apply object expiration or auto-deletion lifecycle rules to the **artifacts bucket** (e.g. `aws_s3_bucket.artifacts`). This bucket stores the Psoxy deployment JAR/ZIP. If the JAR is deleted, the next Terraform plan/apply will fail or force a full rebuild and re-upload.

#### Example Configuration

You can gather the relevant data buckets (which are exported as outputs from the module/host) and apply standard lifecycle rules to them (such as aborting incomplete multipart uploads after 7 days) as shown below:

```hcl
locals {
  # Gather data buckets created by the modules, excluding the deployment artifacts bucket
  buckets_to_secure = merge(
    { for k, v in module.psoxy.bulk_connector_instances : "${k}_input" => v.input_bucket },
    { for k, v in module.psoxy.bulk_connector_instances : "${k}_sanitized" => v.sanitized_bucket },
    module.psoxy.lookup_output_buckets,
  )
}

resource "aws_s3_bucket_lifecycle_configuration" "data_buckets" {
  for_each = local.buckets_to_secure

  bucket = each.value

  rule {
    id     = "abort-incomplete-multipart-uploads"
    status = "Enabled"

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}
```


## Lambda Execution Role
*beta* - released from v0.4.50; YMMV, and may be subject to change.

The terraform modules we provide provision execution roles for each lambda function, and attach by default attach the appropriate AWS Managed Policy to each.

Specifically, this is [`AWSLambdaBasicExecutionRole`](https://docs.aws.amazon.com/aws-managed-policy/latest/reference/AWSLambdaBasicExecutionRole.html), unless you're using a VPC - in which case it is `AWSLambdaVPCAccessExecutionRole`(https://docs.aws.amazon.com/aws-managed-policy/latest/reference/AWSLambdaVPCAccessExecutionRole.html).

For organizations that don't allow use of AWS Managed Policies, you can use the `aws_lambda_execution_role_policy_arn` variable to pass in an alternative which will be used INSTEAD of the AWS Managed Policy.

## Least-Privileged IAM Policy for Provisioning

YMMV, but we exposed a minimal IAM policy for provisioning in the `psoxy-constants` module, which you attach to your desired role to ensure it has sufficient permissions to provision the proxy.

NOTE: using features beyond the default set, such as AWS API Gateway, VPC, or Secrets Manager, may require some additional permissions beyond what is provided in the least-privileged policy.

```hcl
module "psoxy_constants" {
  source = "git::https://github.com/worklytics/psoxy//infra/modules/psoxy-constants?ref=v0.4.61"
}

resource "aws_iam_policy" "min_provisioner_policy" {
    name   = "PsoxyMinProvisioner"
    policy = module.psoxy_constants.aws_least_privileged_policy
}

resource "aws_iam_role_policy_attachment" "min_provisioner_policy" {
    policy_arn         = aws_iam_policy.min_provisioner_policy.arn
    role               = "{{NAME_OF_YOUR_AWS_PROVISIONER_ROLE}}"
}
```
