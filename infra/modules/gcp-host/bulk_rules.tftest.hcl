
# Test for bulk connector rules processing in gcp-host module
# Verifies that columnsToPseudonymizeIfPresent and other rules are properly passed through

variables {
  gcp_project_id       = "test-project-123456"
  environment_name     = "test"
  worklytics_sa_emails = ["test@example.com"]
  psoxy_base_dir       = "../../../" # Use actual path for validation

  # Test custom_bulk_connectors with columnsToPseudonymizeIfPresent
  bulk_connectors = {
    "test-hris" = {
      source_kind = "hris"
      rules = {
        pseudonymFormat                = "URL_SAFE_TOKEN"
        columnsToPseudonymize          = ["employee_id", "manager_id"]
        columnsToPseudonymizeIfPresent = ["optional_field", "backup_email"]
        columnsToDuplicate = {
          "employee_id" = "employee_id_original"
        }
        columnsToRedact = ["salary"]
        columnsToRename = {
          "old_name" = "new_name"
        }
      }
    }
  }

  # Test custom_bulk_connector_rules (takes precedence over rules specified directly in the connector spec)
  custom_bulk_connector_rules = {
    "test-hris" = {
      # This SHOULD be used since custom_bulk_connector_rules takes precedence over the connector spec
      columnsToPseudonymize          = ["wrong_field"]
      columnsToPseudonymizeIfPresent = ["wrong_optional_field"]
    }
    "test-fallback" = {
      # This should be used since test-fallback doesn't exist in bulk_connectors
      columnsToPseudonymize          = ["fallback_id"]
      columnsToPseudonymizeIfPresent = ["fallback_optional"]
    }
  }

  api_connectors     = {}
  webhook_collectors = {}
}

# Mock provider since we're only testing the logic, not actual GCP resources
mock_provider "google" {
  mock_data "google_compute_default_service_account" {
    defaults = {
      email = "123456789-compute@developer.gserviceaccount.com"
      name  = "projects/test-project-123456/serviceAccounts/123456789-compute@developer.gserviceaccount.com"
    }
  }
}

run "setup" {
  command = plan
}

run "rules_environment_variable_set" {
  command = plan

  # Test that RULES environment variable is properly set
  assert {
    error_message = "RULES environment variable should be set for test-hris"
    condition     = can(run.setup.bulk_connector["test-hris"].function_config.service_config[0].environment_variables.RULES)
  }
}

run "decode_hris" {
  command = plan
  module {
    source = "./tests/decode_rules"
  }
  variables {
    encoded = run.setup.bulk_connector["test-hris"].function_config.service_config[0].environment_variables.RULES
  }
}

run "rules_contains_valid_yaml" {
  command = plan

  # Test that RULES contains valid YAML
  assert {
    error_message = "RULES should contain valid YAML that can be decoded"
    condition     = can(run.decode_hris.decoded)
  }
}

run "columnsToPseudonymizeIfPresent_present" {
  command = plan

  # Test that columnsToPseudonymizeIfPresent is included in the YAML
  assert {
    error_message = "columnsToPseudonymizeIfPresent should be present in RULES YAML"
    condition     = contains(keys(run.decode_hris.decoded), "columnsToPseudonymizeIfPresent")
  }
}

run "columnsToPseudonymizeIfPresent_values" {
  command = plan

  # Test that the values are correct (should be from custom_bulk_connector_rules, not bulk_connectors)
  assert {
    error_message = "columnsToPseudonymizeIfPresent should contain 'wrong_optional_field' from custom_bulk_connector_rules"
    condition     = contains(run.decode_hris.decoded.columnsToPseudonymizeIfPresent, "wrong_optional_field")
  }
}

run "other_rule_types_preserved" {
  command = plan

  # Test that other rule types are also preserved
  assert {
    error_message = "columnsToPseudonymize should be preserved"
    condition     = contains(keys(run.decode_hris.decoded), "columnsToPseudonymize")
  }

  assert {
    error_message = "columnsToDuplicate should be preserved"
    condition     = contains(keys(run.decode_hris.decoded), "columnsToDuplicate")
  }

  assert {
    error_message = "columnsToRedact should be preserved"
    condition     = contains(keys(run.decode_hris.decoded), "columnsToRedact")
  }

  assert {
    error_message = "columnsToRename should be preserved"
    condition     = contains(keys(run.decode_hris.decoded), "columnsToRename")
  }

  assert {
    error_message = "pseudonymFormat should be preserved"
    condition     = contains(keys(run.decode_hris.decoded), "pseudonymFormat")
  }
}

run "rules_precedence_test" {
  command = plan

  # Test that custom_bulk_connector_rules takes precedence over bulk_connectors rules (as intended)
  # The test-hris should use "wrong_field" from custom_bulk_connector_rules, not "employee_id" from bulk_connectors
  assert {
    error_message = "Should use rules from custom_bulk_connector_rules (which takes precedence)"
    condition     = contains(run.decode_hris.decoded.columnsToPseudonymize, "wrong_field")
  }

  assert {
    error_message = "Should NOT use rules from bulk_connectors when custom_bulk_connector_rules is present"
    condition     = !contains(run.decode_hris.decoded.columnsToPseudonymize, "employee_id")
  }
}

run "setup_fallback" {
  command = plan

  variables {
    # Override to test fallback behavior
    bulk_connectors = {
      "test-fallback" = {
        source_kind = "custom"
        # No rules specified, should fall back to custom_bulk_connector_rules
      }
    }
  }
}

run "decode_fallback" {
  command = plan
  module {
    source = "./tests/decode_rules"
  }
  variables {
    encoded = run.setup_fallback.bulk_connector["test-fallback"].function_config.service_config[0].environment_variables.RULES
  }
}

run "fallback_behavior" {
  command = plan

  # Test that custom_bulk_connector_rules is used as fallback
  assert {
    error_message = "Should fall back to custom_bulk_connector_rules when bulk_connectors.rules is null"
    condition     = can(run.decode_fallback.decoded)
  }

  assert {
    error_message = "Fallback should include columnsToPseudonymizeIfPresent from custom_bulk_connector_rules"
    condition     = contains(keys(run.decode_fallback.decoded), "columnsToPseudonymizeIfPresent")
  }
}

run "setup_rules_file" {
  command = plan

  variables {
    bulk_connectors = {
      "workdata-generic" = {
        source_kind = "workdata-generic"
        rules_file  = "docs/sources/workdata-generic/workdata-generic.yaml"
      }
    }
    custom_bulk_connector_rules = {}
  }
}

run "rules_file_relative_to_psoxy_base_dir" {
  command = plan

  assert {
    error_message = "RULES should be loaded from rules_file relative to psoxy_base_dir"
    condition     = can(run.setup_rules_file.bulk_connector["workdata-generic"].function_config.service_config[0].environment_variables.RULES)
  }
}

run "setup_rules_file_in_terraform_root" {
  command = plan

  variables {
    bulk_connectors = {
      "local-rules" = {
        source_kind = "hris"
        rules_file  = "tests/fixtures/calendar.yaml"
      }
    }
    custom_bulk_connector_rules = {}
  }
}

run "rules_file_relative_to_terraform_root" {
  command = plan

  assert {
    error_message = "RULES should be loaded from rules_file relative to the Terraform root module"
    condition     = can(run.setup_rules_file_in_terraform_root.bulk_connector["local-rules"].function_config.service_config[0].environment_variables.RULES)
  }
}