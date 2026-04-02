# test to ensure no connector spec defines both 'rules' and 'rules_file'
# and that rules_raw is properly computed from rules_file when base_dir is set

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

run "validate_no_mutual_rules_and_rules_raw" {
  command = plan

  assert {
    condition = alltrue([
      for k, v in output.available_bulk_connectors :
      try(v.rules, null) == null || try(v.rules_raw, null) == null
    ])
    error_message = "A bulk connector must not define both 'rules' (structured) and 'rules_raw' (from rules_file)."
  }
}

run "validate_api_connectors_no_mutual_rules_raw_and_rules" {
  command = plan

  # API connectors shouldn't have both rules and rules_raw
  # (API connectors don't currently have 'rules', but this guards against future additions)
  assert {
    condition = alltrue([
      for k, v in output.available_oauth_data_source_connectors :
      try(v.rules, null) == null || try(v.rules_raw, null) == null
    ])
    error_message = "An API connector must not define both 'rules' and 'rules_raw'."
  }
}

run "validate_rules_raw_null_without_base_dir" {
  command = plan

  # Without base_dir set, all rules_raw should be null
  assert {
    condition = alltrue([
      for k, v in output.available_bulk_connectors :
      try(v.rules_raw, null) == null
    ])
    error_message = "rules_raw should be null when base_dir is not set."
  }
}

run "validate_rules_raw_computed_with_base_dir" {
  command = plan

  variables {
    base_dir = "../../../"
  }

  # workdata-generic has rules_file set, so rules_raw should be non-null when base_dir is provided
  assert {
    condition     = try(output.available_bulk_connectors["workdata-generic"].rules_raw, null) != null
    error_message = "workdata-generic should have rules_raw set when base_dir is provided."
  }

  # workdata-generic should NOT have rules (structured) since it uses rules_file
  assert {
    condition     = try(output.available_bulk_connectors["workdata-generic"].rules, null) == null
    error_message = "workdata-generic should not have structured rules when using rules_file."
  }

  # hris has inline rules, should NOT have rules_raw
  assert {
    condition     = try(output.available_bulk_connectors["hris"].rules_raw, null) == null
    error_message = "hris should not have rules_raw since it doesn't define rules_file."
  }

  # hris should still have inline rules
  assert {
    condition     = try(output.available_bulk_connectors["hris"].rules, null) != null
    error_message = "hris should still have structured rules."
  }
}
