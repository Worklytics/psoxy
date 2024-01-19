moved {
  from   = aws_iam_policy.execution_lambda_to_caller
  to     = aws_iam_policy.execution_lambda_to_caller[0]
}

moved {
  from = aws_iam_role_policy_attachment.invoker_url_lambda_execution
  to   = aws_iam_role_policy_attachment.invoker_url_lambda_execution[0]
}
