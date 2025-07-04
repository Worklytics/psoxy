# This module creates an S3 bucket for  output data from a proxy instance.
#
# TODO: unify with aws-psoxy-output-bucket module? why is that different?
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

#  - public access block?
#  - lifecycle policy with expiration? (eg 720 days)
#  - versioning?
# or invert-control for all of the above, and let caller do it if they care??
