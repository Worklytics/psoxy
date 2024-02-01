# 0.4.47 will add support for Secrets Manager instead of SSM Parameter Store
# customers will seem moves
moved {
  from = module.global_secrets
  to   = module.global_secrets_ssm[0]
}

moved {
  from = module.instance_secrets
  to   = module.instance_ssm_parameters
}
