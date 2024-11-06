module "env_id" {
  source = "../env-id"

  environment_name          = var.environment_name
  supported_word_delimiters = ["-"]
  preferred_word_delimiter  = "-"
}


resource "aws_s3_bucket" "output" {
  # note: this ends up with a long UTC time-stamp + random number appended to it to form the bucket name
  bucket_prefix = "${module.env_id.id}-${var.instance_id}-"

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

resource "aws_s3_bucket_public_access_block" "sanitized" {
  count = var.provision_bucket_public_access_block ? 1 : 0

  bucket = aws_s3_bucket.output.bucket

  block_public_acls       = true
  block_public_policy     = true
  restrict_public_buckets = true
  ignore_public_acls      = true
}


# proxy's lambda needs to WRITE to the output bucket
resource "aws_iam_policy" "sanitized_bucket_write_policy" {
  name        = "${module.env_id.id}_BucketWrite_${aws_s3_bucket.output.id}"
  description = "Allow principal to write to bucket: ${aws_s3_bucket.output.id}"

  policy = jsonencode(
    {
      "Version" : "2012-10-17",
      "Statement" : [
        {
          "Action" : [
            "s3:PutObject",
          ],
          "Effect" : "Allow",
          "Resource" : "${aws_s3_bucket.output.arn}/*"
        }
      ]
  })

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}


resource "aws_iam_role_policy_attachment" "write_policy_for_sanitized_bucket" {
  role       = var.iam_role_for_lambda_name
  policy_arn = aws_iam_policy.sanitized_bucket_write_policy.arn
}

# proxy caller (data consumer) needs to read (both get and list objects) from the output bucket
resource "aws_iam_policy" "sanitized_bucket_read" {
  name        = "${module.env_id.id}_BucketRead_${aws_s3_bucket.output.id}"
  description = "Allow to read content from bucket: ${aws_s3_bucket.output.id}"

  policy = jsonencode(
    {
      "Version" : "2012-10-17",
      "Statement" : [
        {
          "Action" : [
            "s3:GetObject",
            "s3:ListBucket"
          ],
          "Effect" : "Allow",
          "Resource" : [
            "${aws_s3_bucket.output.arn}",
            "${aws_s3_bucket.output.arn}/*"
          ]
        }
      ]
  })

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

locals {
  accessor_role_names = var.sanitized_accessor_role_names
}

resource "aws_iam_role_policy_attachment" "reader_policy_to_accessor_role" {
  for_each = toset([for r in local.accessor_role_names : r if r != null])

  role       = each.key
  policy_arn = aws_iam_policy.sanitized_bucket_read.arn
}

# to facilitate composition of output pipeline
output "output_bucket" {
  value = aws_s3_bucket.output.bucket
}
