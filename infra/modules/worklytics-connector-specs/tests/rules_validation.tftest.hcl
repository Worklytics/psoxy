# test to ensure no connector spec defines both 'rules' and 'rules_file'

variables {
  enabled_connectors = []
}

run "validate_no_mutual_rules_and_rules_file" {
  command = plan

  assert {
    condition = alltrue([
      for k, v in output.available_bulk_connectors :
      try(v.rules, null) == null || try(v.rules_file, null) == null
    ])
    error_message = "A bulk connector must not define both 'rules' and 'rules_file'."
  }
}

run "validate_api_connectors_no_mutual_rules_and_rules_file" {
  command = plan

  assert {
    condition = alltrue([
      for k, v in merge(output.available_oauth_data_source_connectors, output.available_google_workspace_connectors, output.available_msft_365_connectors) :
      try(v.rules, null) == null || try(v.rules_file, null) == null
    ])
    error_message = "An API connector must not define both 'rules' (YAML content) and 'rules_file'."
  }
}

run "validate_api_connectors_no_mutual_rules_and_rules_raw" {
  command = plan

  # API connectors use rules (YAML string) when provided inline; rules_raw is deprecated on api_connectors
  assert {
    condition = alltrue([
      for k, v in merge(output.available_oauth_data_source_connectors, output.available_google_workspace_connectors, output.available_msft_365_connectors) :
      try(v.rules, null) == null || try(v.rules_raw, null) == null
    ])
    error_message = "An API connector must not define both 'rules' and 'rules_raw'."
  }
}

run "validate_api_connectors_have_rules_file" {
  command = plan

  assert {
    condition     = try(output.available_oauth_data_source_connectors["asana"].rules_file, null) != null
    error_message = "asana should declare rules_file in connector specs."
  }

  assert {
    condition     = try(output.available_google_workspace_connectors["gcal"].rules_file, null) != null
    error_message = "gcal should declare rules_file in connector specs."
  }

  assert {
    condition     = try(output.available_msft_365_connectors["outlook-cal"].rules_file, null) != null
    error_message = "outlook-cal should declare rules_file in connector specs."
  }
}

run "validate_bulk_connectors_with_rules_file" {
  command = plan

  assert {
    condition     = try(output.available_bulk_connectors["hris"].rules_file, null) != null
    error_message = "hris should declare rules_file in connector specs."
  }

  assert {
    condition     = try(output.available_bulk_connectors["hris"].rules, null) == null
    error_message = "hris should not have structured rules when using rules_file."
  }
}
