variables {
  enabled_connectors = []
}



// NOTE: terraform test output can sometimes give red-herring feedback on failures; namely info about variable value types, (X is object with 10 attributes),
/// which may not be the problem at all

run "oauth_refresh_token_locks" {
  command = apply

  assert {
    error_message = "6 oauth connectors expected to USE_SHARED_TOKEN"
    condition = 6 == length([ for k, v in output.available_oauth_data_source_connectors :
      v if try(lower(v.environment_variables.USE_SHARED_TOKEN), "false") == "true"])
  }

  assert {
    # filter this
    error_message = "All oauth connectors with USE_SHARED_TOKEN set to TRUE must have OAUTH_REFRESH_TOKEN secured variable"
    condition = alltrue([
      for k, v in output.available_oauth_data_source_connectors :
      try(lower(v.environment_variables.USE_SHARED_TOKEN), "false") == "false" || anytrue([
      for var in v.secured_variables : var.name == "OAUTH_REFRESH_TOKEN"])
      ])
  }
}
