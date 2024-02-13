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
local {
  id_of_bucket_to_store_logs = "{YOUR_BUCKET_ID_HERE}"
}

resource "aws_s3_bucket_logging" "logging" {
  for_each = module.psoxy.bulk_connector_instances

  bucket = each.value.sanitized_bucket_name

  target_bucket = local.id_of_bucket_to_store_logs
  target_prefix = "psoxy/${each.key}/"
}

resource "aws_s3_bucket_logging" "logging" {
  for_each = module.psoxy.bulk_connector_instances

  bucket = each.value.input_bucket_name

  target_bucket = local.id_of_bucket_to_store_logs
  target_prefix = "psoxy/${each.key}/"
}
```

Analogous approaches can be used to configure versioning, replication, etc;

- [`aws_s3_bucket_versioning`](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket_versioning)
- [`aws_s3_bucket_replication`](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket_replication)

Note that encryption, lifecycle, public_access_block are set by the Workltyics-provided modules, so
you may have conflicts issues if you also try to set those outside.
