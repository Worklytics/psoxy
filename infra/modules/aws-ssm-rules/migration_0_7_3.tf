# Rules SSM parameter uses count when rules may be stored in S3 instead.

moved {
  from = aws_ssm_parameter.rules
  to   = aws_ssm_parameter.rules[0]
}
