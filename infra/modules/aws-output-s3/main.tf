# This module creates an S3 bucket for output data from a proxy instance.
#
# TODO: unify with aws-psoxy-output-bucket module? why is that different?
#  - that carries implicit policy, which is not ideal vs combining single unified policies
#
#
# NOTE: this will provision NEITHER read nor write permissions to the bucket; to reduce number of IAM policies
# + attachments, we're going to architect things as policy per principal/role, rather than per resource
#

module "env_id" {
  source = "../env-id"

  environment_name          = var.environment_name
  supported_word_delimiters = ["-"]
  preferred_word_delimiter  = "-"
}

locals {
  bucket_name_prefix          = "${module.env_id.id}-${replace(var.instance_id, "_", "-")}"
  bucket_name_unique_sequence = coalesce(var.unique_sequence, uuid())
}

resource "aws_s3_bucket" "bucket" {
  bucket = "${local.bucket_name_prefix}-${local.bucket_name_unique_sequence}-${var.bucket_suffix}"

  lifecycle {
    ignore_changes = [
      bucket, # avoid re-creating bucket if our naming conventions change *or*, if relying on uuid() for uniqueness, as that value changes on every plan/apply
      tags    # avoid churning tags that may be managed outside of this terraform module
    ]
  }
}

#  - versioning?


resource "aws_s3_bucket_public_access_block" "output" {
  count = var.provision_bucket_public_access_block ? 1 : 0

  bucket = aws_s3_bucket.output.bucket

  block_public_acls       = true
  block_public_policy     = true
  restrict_public_buckets = true
  ignore_public_acls      = true
}

resource "aws_s3_bucket_lifecycle_configuration" "ttl" {
  count = (var.lifecycle_ttl_days == null || var.lifecycle_ttl_days == 0) ? 0 : 1

  bucket = aws_s3_bucket.output.bucket

  rule {
    id     = "expire-after-${var.lifecycle_ttl_days}-days"
    status = "Enabled"
    expiration {
      days = var.lifecycle_ttl_days
    }
  }
}
