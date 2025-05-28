# Module to generate IAM policy statements for read/write access to an S3 bucket, rather than repeat it for every side output


locals {
  without_protocol = try(replace(var.s3_path, "s3://", ""), "")

  directory_wildcard = endswith(local.without_protocol, "/") ? "*" : "/*"

  iam_statements = var.s3_path == null ? [] : [
    {
      Sid = "AllowS3_${replace(var.s3_path, "/", "_")}"
      Action = [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject",
        "s3:ListBucket"
      ]
      Effect = "Allow"
      Resource = [
        "arn:aws:s3:::${var.s3_path}",
        "arn:aws:s3:::${var.s3_path}${local.directory_wildcard}"
      ]
    }
  ]
}

output "iam_statements" {
  value = local.iam_statements
}
