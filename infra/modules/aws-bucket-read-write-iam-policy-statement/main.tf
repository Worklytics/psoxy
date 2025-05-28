# Module to generate IAM policy statements for read/write access to an S3 bucket, rather than repeat it for every side output


locals {
  without_protocol = try(replace(var.s3_path, "s3://", ""), "")

  directory_wildcard = endswith(local.without_protocol, "/") ? "*" : "/*"
}

output "iam_statements" {
  value = var.s3_path == null ? [] : [
    {
      Sid = "AllowS3-${replace(local.without_protocol, "/", "-")}"
      Action = [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject",
        "s3:ListBucket"
      ]
      Effect = "Allow"
      Resource = [
        "arn:aws:s3:::${local.without_protocol}",
        "arn:aws:s3:::${local.without_protocol}${local.directory_wildcard}"
      ]
    }
  ]
}
