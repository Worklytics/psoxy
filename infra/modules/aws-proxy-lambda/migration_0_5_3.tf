moved {
  from = aws_iam_policy.ssm_param_policy
  to   = aws_iam_policy.required_resource_access
}
