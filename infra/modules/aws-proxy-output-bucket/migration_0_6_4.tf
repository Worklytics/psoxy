# Output-bucket read IAM moved to aws-host in 0.6.4.

removed {
  from = aws_iam_policy.sanitized_bucket_read

  lifecycle {
    destroy = true
  }
}

removed {
  from = aws_iam_role_policy_attachment.reader_policy_to_accessor_role

  lifecycle {
    destroy = true
  }
}
