# Bulk rules provisioning moved into aws-ssm-rules (tier/compression).

moved {
  from = aws_ssm_parameter.rules
  to   = module.rules_parameter.aws_ssm_parameter.rules
}
