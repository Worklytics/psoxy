
moved {
  from = aws_s3_bucket.output
  to   = module.sanitized_output_bucket.output
}

moved {
  from = aws_s3_bucket_server_side_encryption_configuration.sanitized
  to   = module.sanitized_output_bucket.aws_s3_bucket_server_side_encryption_configuration.sanitized
}

moved {
  from = aws_s3_bucket_public_access_block.sanitized
  to   = module.sanitized_output_bucket.aws_s3_bucket_public_access_block.sanitized
}

moved {
  from = aws_iam_policy.sanitized_bucket_write_policy
  to   = module.sanitized_output_bucket.aws_iam_policy.sanitized_bucket_write_policy
}

moved {
  from = aws_iam_role_policy_attachment.write_policy_for_sanitized_bucket
  to   = module.sanitized_output_bucket.aws_iam_role_policy_attachment.write_policy_for_sanitized_bucket
}

moved {
  from = aws_iam_policy.sanitized_bucket_read
  to   = module.sanitized_output_bucket.aws_iam_policy.sanitized_bucket_read
}

moved {
  from = aws_iam_role_policy_attachment.reader_policy_to_accessor_role
  to   = module.sanitized_output_bucket.aws_iam_role_policy_attachment.reader_policy_to_accessor_role
}
