variables {
  enabled_connectors = ["gcal"]
}

run "gws_default_uses_service_account_key" {
  command = plan

  assert {
    condition = alltrue([
      for k, v in output.available_google_workspace_connectors :
      v.source_auth_strategy == "gcp_service_account_key"
    ])
    error_message = "Google Workspace connectors must default to source_auth_strategy gcp_service_account_key."
  }

  assert {
    condition     = output.enabled_google_workspace_connectors["gcal"].source_auth_strategy == "gcp_service_account_key"
    error_message = "Enabled gcal connector must default to gcp_service_account_key."
  }
}

run "gws_wif_uses_gcp_iam_sign_jwt" {
  command = plan

  variables {
    enabled_connectors = ["gcal", "gdirectory"]
    google_workspace_connector_settings = {
      api_client_auth_method = "workload_identity_federation"
    }
  }

  assert {
    condition = alltrue([
      for k, v in output.available_google_workspace_connectors :
      v.source_auth_strategy == "gcp_iam_sign_jwt"
    ])
    error_message = "api_client_auth_method workload_identity_federation must set source_auth_strategy to gcp_iam_sign_jwt."
  }

  assert {
    condition     = output.enabled_google_workspace_connectors["gdirectory"].source_auth_strategy == "gcp_iam_sign_jwt"
    error_message = "Enabled gdirectory connector must use gcp_iam_sign_jwt when opted into WIF."
  }
}
