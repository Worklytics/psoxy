# This module creates an S3 bucket for side output data from a proxy instance.

# not really needed, but nicer imho than relying on AWS to generate a random bucket suffix
# arguably should allow it to be passed in, if we want to accommodate side output of bulk connectors
resource "random_string" "bucket_suffix" {
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
  bucket_name_prefix = "${module.env_id.id}-${replace(var.instance_id, "_", "-")}"
}

resource "aws_s3_bucket" "side_output" {
  # q: add an index? 0, 1, 2, ... ? in case multiple side outputs (original, sanitized, etc.)?
  bucket = "${local.bucket_name_prefix}-side-output"

  # q: tag as 'sanitized'? NO.
  #   - then we can't ignore changes to tags, which brings possibility of conflicting with customer's tags
  #  - and it's possible to have mix of sanitized and non-sanitized data in the same bucket, if customer changes configurations

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

