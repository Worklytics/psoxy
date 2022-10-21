
# Migration
# if you deployed a version of this example (gcp-google-workspace) prior to 0.4.8, and want to
# upgrade it to 0.4.8 structure, add the following lines to main.tf and terrafrom apply; after one
# apply, you can remove them

moved {
  from = module.worklytics_connector_specs
  to   = module.psoxy-gcp-google-workspace.module.worklytics_connector_specs
}

moved {
  from = module.psoxy-gcp
  to   = module.psoxy-gcp-google-workspace.module.psoxy-gcp
}

moved {
  from = module.global_secrets
  to   = module.psoxy-gcp-google-workspace.module.global_secrets
}

moved {
  from = module.google-workspace-connection
  to   = module.psoxy-gcp-google-workspace.module.google-workspace-connection
}

moved {
  from = module.google-workspace-connection-auth
  to   = module.psoxy-gcp-google-workspace.module.google-workspace-connection-auth
}

moved {
  from = module.google-workspace-key-secrets
  to   = module.psoxy-gcp-google-workspace.module.google-workspace-key-secrets
}

moved {
  from = module.psoxy-google-workspace-connector
  to   = module.psoxy-gcp-google-workspace.module.psoxy-google-workspace-connector
}

moved {
  from = module.worklytics-psoxy-connection-google-workspace
  to   = module.psoxy-gcp-google-workspace.module.worklytics-psoxy-connection-google-workspace
}

moved {
  from = google_service_account.long_auth_connector_sa
  to   = module.psoxy-gcp-google-workspace.google_service_account.long_auth_connector_sa
}

moved {
  from = module.connector-long-auth-block
  to   = module.psoxy-gcp-google-workspace.module.connector-long-auth-block
}

moved {
  from = module.long-auth-token-secret-fill-instructions
  to   = module.psoxy-gcp-google-workspace.module.long-auth-token-secret-fill-instructions
}

moved {
  from = module.connector-long-auth-create-function
  to   = module.psoxy-gcp-google-workspace.module.connector-long-auth-function
}

moved {
  from = module.worklytics-psoxy-connection-long-auth
  to   = module.psoxy-gcp-google-workspace.module.worklytics-psoxy-connection-long-auth
}

moved {
  from = module.psoxy-gcp-bulk
  to   = module.psoxy-gcp-google-workspace.module.psoxy-gcp-bulk
}
