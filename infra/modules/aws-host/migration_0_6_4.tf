# IAM policies consolidated at host level in 0.6.4; prior per-connector policies are replaced.

removed {
  from = aws_iam_policy.execution_lambda_to_caller

  lifecycle {
    destroy = true
  }
}

removed {
  from = aws_iam_role_policy_attachment.invoker_url_lambda_execution

  lifecycle {
    destroy = true
  }
}

removed {
  from = aws_iam_policy.async_output_access

  lifecycle {
    destroy = true
  }
}

removed {
  from = aws_iam_role_policy_attachment.async_output_access_to_caller

  lifecycle {
    destroy = true
  }
}

removed {
  from = aws_iam_policy.bulk_testing

  lifecycle {
    destroy = true
  }
}

removed {
  from = aws_iam_role_policy_attachment.bulk_testing

  lifecycle {
    destroy = true
  }
}

removed {
  from = aws_iam_user_policy_attachment.bulk_testing

  lifecycle {
    destroy = true
  }
}

# Replaced by per-lookup-table lookup_bucket_read policies (scoped to each lookup bucket).
removed {
  from = aws_iam_policy.output_bucket_read

  lifecycle {
    destroy = true
  }
}

removed {
  from = aws_iam_role_policy_attachment.output_bucket_read

  lifecycle {
    destroy = true
  }
}
