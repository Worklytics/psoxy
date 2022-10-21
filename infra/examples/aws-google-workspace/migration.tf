
# Migration
# if you deployed a version of this example (aws-google-workspace) prior to 0.4.8, and want to
# upgrade it to 0.4.8 structure, add the following lines to main.tf and terrafrom apply; after one
# apply, you can remove them

moved {
  from = module.worklytics_connector_specs
  to   = module.aws-google-workspace.module.worklytics_connector_specs
}

moved {
  from = module.psoxy-aws
  to   = module.aws-google-workspace.module.psoxy-aws
}

moved {
  from = module.global_secrets
  to   = module.aws-google-workspace.module.global_secrets
}

moved {
  from = module.google-workspace-connection
  to   = module.aws-google-workspace.module.google-workspace-connection
}

moved {
  from = module.google-workspace-connection-auth
  to   = module.aws-google-workspace.module.google-workspace-connection-auth
}

moved {
  from = module.sa-key-secrets
  to   = module.aws-google-workspace.module.sa-key-secrets
}

moved {
  from = module.psoxy-google-workspace-connector
  to   = module.aws-google-workspace.module.psoxy-google-workspace-connector
}

moved{
  from = module.worklytics-psoxy-connection-google-workspace
  to   = module.aws-google-workspace.module.worklytics-psoxy-connection-google-workspace
}

moved {
  from = aws_ssm_parameter.long-access-secrets
  to   = module.aws-google-workspace.aws_ssm_parameter.long-access-secrets
}

moved {
  from = module.parameter-fill-instructions
  to   = module.aws-google-workspace.module.parameter-fill-instructions
}

moved {
  from = module.source_token_external_todo
  to   = module.aws-google-workspace.module.source_token_external_todo
}

moved {
  from = module.aws-psoxy-long-auth-connectors
  to   = module.aws-google-workspace.module.aws-psoxy-long-auth-connectors
}

moved {
  from = module.worklytics-psoxy-connection
  to   = module.aws-google-workspace.module.worklytics-psoxy-connection
}

moved {
  from = module.psoxy-bulk
  to   = module.aws-google-workspace.module.psoxy-bulk
}

