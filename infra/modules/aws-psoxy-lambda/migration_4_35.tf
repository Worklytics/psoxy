//migrations that pre-dated 0.4.36, so just capturing as of 0.4.35

moved {
  from = aws_cloudwatch_log_group.lambda-log
  to   = aws_cloudwatch_log_group.lambda_log
}

moved {
  from = aws_lambda_function.psoxy-instance
  to   = aws_lambda_function.instance
}
