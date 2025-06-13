# This module creates an S3 bucket for  output data from a proxy instance.
#
# TODO: unify with aws-psoxy-output-bucket module? why is that different?
#
# NOTE: this will provision NEITHER read nor write permissions to the bucket; to reduce number of IAM policies
# + attachments, we're going to architect things as policy per principal/role, rather than per resource
#
# not really needed, but nicer imho than relying on AWS to generate a random bucket suffix
# arguably should allow it to be passed in, if we want to accommodate side output of bulk connectors
resource "random_string" "unique_sequence" {
  count = var.unique_sequence == null ? 1 : 0

  length  = 8
  lower   = true
  upper   = false
  special = false
}

module "env_id" {
  source = "../env-id"

  environment_name          = var.environment_name
  supported_word_delimiters = ["-"]
  preferred_word_delimiter  = "-"
}

locals {
  bucket_name_prefix          = "${module.env_id.id}-${replace(var.instance_id, "_", "-")}"
  bucket_name_unique_sequence = coalesce(var.unique_sequence, try(random_string.unique_sequence[0].result, ""))
}

resource "aws_s3_bucket" "bucket" {
  bucket = "${local.bucket_name_prefix}-${local.bucket_name_unique_sequence}-${var.bucket_suffix}"

  lifecycle {
    ignore_changes = [
      # bucket, # avoid re-creating bucket if our naming conventions change
      tags # avoid churning tags that may be managed outside of this terraform module
    ]
  }
}

#  - public access block?
#  - lifecycle policy with expiration? (eg 720 days)
#  - versioning?
# or invert-control for all of the above, and let caller do it if they care??
