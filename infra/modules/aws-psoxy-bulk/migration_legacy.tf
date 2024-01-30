
moved {
  from = aws_iam_policy.output_bucket_write_policy
  to   = aws_iam_policy.sanitized_bucket_write_policy
}

moved {
  from = aws_iam_role_policy_attachment.write_policy_for_output_bucket
  to   = aws_iam_role_policy_attachment.write_policy_for_sanitized_bucket
}

moved {
  from = aws_iam_policy.output_bucket_read
  to   = aws_iam_policy.sanitized_bucket_read
}

moved {
  from = aws_s3_bucket.output
  to   = aws_s3_bucket.sanitized
}

moved {
  from = aws_s3_bucket_public_access_block.output-block-public-access
  to   = aws_s3_bucket_public_access_block.sanitized
}
