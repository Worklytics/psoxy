
# Migration
# if you deployed a version of this example (psoxy-aws-msft-365) prior to 0.4.8, and want to
# upgrade it to 0.4.8 structure, add the following lines to main.tf and terrafrom apply; after one
# apply, you can remove them

moved {
  from = module.worklytics_connector_specs
  to   = module.psoxy-aws-msft-365.module.worklytics_connector_specs
}

moved {
  from = module.psoxy-aws
  to   = module.psoxy-aws-msft-365.module.psoxy-aws
}

moved {
  from = module.global_secrets
  to   = module.psoxy-aws-msft-365.module.global_secrets
}

moved {
  from = module.msft-connection
  to   = module.psoxy-aws-msft-365.module.msft-connection
}

moved {
  from = module.msft-connection-auth
  to   = module.psoxy-aws-msft-365.module.msft-connection-auth
}

moved {
  from = module.private-key-aws-parameters
  to   = module.psoxy-aws-msft-365.module.msft-365-connector-key-secrets
}

moved {
  from = module.psoxy-msft-connector
  to   = module.psoxy-aws-msft-365.module.psoxy-msft-connector
}

moved {
  from = module.worklytics-psoxy-connection-msft-365
  to   = module.psoxy-aws-msft-365.module.worklytics-psoxy-connection-msft-365
}

moved {
  from = aws_ssm_parameter.long-access-secrets
  to   = module.psoxy-aws-msft-365.aws_ssm_parameter.long-access-secrets
}

moved {
  from = module.parameter-fill-instructions
  to   = module.psoxy-aws-msft-365.module.parameter-fill-instructions
}

moved {
  from = module.source_token_external_todo
  to   = module.psoxy-aws-msft-365.module.source_token_external_todo
}

moved {
  from = module.aws-psoxy-long-auth-connectors
  to   = module.psoxy-aws-msft-365.module.aws-psoxy-long-auth-connectors
}

moved {
  from = module.worklytics-psoxy-connection-oauth-long-access
  to   = module.psoxy-aws-msft-365.module.worklytics-psoxy-connection-oauth-long-access
}

moved {
  from = module.psoxy-bulk
  to   = module.psoxy-aws-msft-365.module.psoxy-bulk
}

