moved {
  from = aws_s3_bucket_public_access_block.sanitized
  to   = aws_s3_bucket_public_access_block.sanitized[0]
}

moved {
  from = aws_s3_bucket_public_access_block.input-block-public-access
  to   = aws_s3_bucket_public_access_block.input-block-public-access[0]
}
