
# for customers coming from >=0.4.46
moved {
   from = module.psoxy.aws_iam_policy.execution_lambda_to_caller[0]
   to   = aws_iam_policy.execution_lambda_to_caller[0]
}

moved {
  from = module.psoxy.aws_iam_role_policy_attachment.invoker_url_lambda_execution[0]
  to   = aws_iam_role_policy_attachment.invoker_url_lambda_execution[0]
}


# for customers coming from <0.4.46, comment the lines above and uncomment those below *or* upgrade
# to an intermediate version first (eg, to something between 0.4.46 and 0.4.58; then to >=0.4.58)

# moved {
#   from = module.psoxy.aws_iam_policy.execution_lambda_to_caller
#   to   = aws_iam_policy.execution_lambda_to_caller[0]
# }
#
# moved {
#   from = module.psoxy.aws_iam_role_policy_attachment.invoker_url_lambda_execution
#   to   = aws_iam_role_policy_attachment.invoker_url_lambda_execution[0]
# }
