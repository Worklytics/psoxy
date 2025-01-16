variables {
  enabled_connectors = []
}



// NOTE: terraform test output can sometimes give red-herring feedback on failures; namely info about variable value types, (X is object with 10 attributes),
/// which may not be the problem at all

// also don't love that these tests are very hard to debug and validate expectations/preconditions simply. conditions end up being very complex/hard-to-read,
// or highly repetitive

// and terraform conditions don't seem to short-circuit, so need to add try()s in successive steps, which can mask other errors

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


run "oauth_refresh_token_access_tokens" {

  assert {
    error_message = "all oauth connectors use ACCESS_TOKEN (all except dropbox??)"
    condition = 10 == length([ for k, v in output.available_oauth_data_source_connectors :
      v if anytrue([for var in v.secured_variables : var.name == "ACCESS_TOKEN"])
      ])
  }

  assert {
    error_message = "all oauth connectors with source_auth_strategy == oauth2_refresh_token have REFRESH_TOKEN secured variable"
    condition = alltrue([
      for k, v in output.available_oauth_data_source_connectors :
        v.source_auth_strategy != "oauth2_refresh_token" ||
         try(v.environment_variables.GRANT_TYPE, "access_token") != "refresh_token" ||  # implicitly, unspecified grant type is an ACCESS_TOKEN or something else long-lived that needn't be refreshed
         anytrue([ for var in v.secured_variables : var.name == "REFRESH_TOKEN"])
      ])
  }

  assert {
    error_message = "all oauth connectors with source_auth_strategy == oauth2_refresh_token must have writable ACCESS_TOKEN secured variable"
    condition = alltrue([
      for k, v in output.available_oauth_data_source_connectors :
      v.source_auth_strategy != "oauth2_refresh_token" ||
    anytrue([ for var in v.secured_variables : var.name == "ACCESS_TOKEN" && var.writable])
      ])
  }

}
