# Output-bucket read IAM moved to aws-host in 0.6.4; bulk testing uses bucket policies.

moved {
  from = aws_s3_bucket_policy.testing_input_upload
  to   = aws_s3_bucket_policy.testing_input
}

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

removed {
  from = aws_iam_policy.testing_sanitized_cleanup

  lifecycle {
    destroy = true
  }
}

removed {
  from = aws_iam_role_policy_attachment.testing_sanitized_cleanup_to_caller_role

  lifecycle {
    destroy = true
  }
}
