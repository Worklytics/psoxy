

# the following creates ambiguity with ../aws-host/migration_0_4_58.tf
# customers moving from < 0.4.46 to >= 0.4.58 should to the following go to ../aws-host/migration_0_4_58.tf
# and uncomment the lines there

# moved {
#   from = aws_iam_policy.execution_lambda_to_caller
#   to   = aws_iam_policy.execution_lambda_to_caller[0]
# }
#
# moved {
#   from = aws_iam_role_policy_attachment.invoker_url_lambda_execution
#   to   = aws_iam_role_policy_attachment.invoker_url_lambda_execution[0]
# }
