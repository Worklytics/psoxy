
# Test for bulk connector rules processing in gcp-host module
# Verifies that columnsToPseudonymizeIfPresent and other rules are properly passed through

variables {
  gcp_project_id       = "test-project-123456"
  environment_name     = "test"
  worklytics_sa_emails = ["test@example.com"]
  psoxy_base_dir       = "/Users/erik/code/psoxy/" # Use actual path for validation

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

  # Test custom_bulk_connector_rules (should be overridden by bulk_connectors rules)
  custom_bulk_connector_rules = {
    "test-hris" = {
      # This should NOT be used since test-hris has rules in bulk_connectors
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
mock_provider "google" {}

run "rules_environment_variable_set" {
  command = plan

  # Test that RULES environment variable is properly set
  assert {
    error_message = "RULES environment variable should be set for test-hris"
    condition     = can(module.bulk_connector["test-hris"].function_config.service_config[0].environment_variables.RULES)
  }
}

run "rules_contains_valid_yaml" {
  command = plan

  # Test that RULES contains valid YAML
  assert {
    error_message = "RULES should contain valid YAML that can be decoded"
    condition     = can(yamldecode(module.bulk_connector["test-hris"].function_config.service_config[0].environment_variables.RULES))
  }
}

run "columnsToPseudonymizeIfPresent_present" {
  command = plan

  # Test that columnsToPseudonymizeIfPresent is included in the YAML
  assert {
    error_message = "columnsToPseudonymizeIfPresent should be present in RULES YAML"
    condition     = contains(keys(yamldecode(module.bulk_connector["test-hris"].function_config.service_config[0].environment_variables.RULES)), "columnsToPseudonymizeIfPresent")
  }
}

run "columnsToPseudonymizeIfPresent_values" {
  command = plan

  # Test that the values are correct (should be from custom_bulk_connector_rules, not bulk_connectors)
  assert {
    error_message = "columnsToPseudonymizeIfPresent should contain 'wrong_optional_field' from custom_bulk_connector_rules"
    condition     = contains(yamldecode(module.bulk_connector["test-hris"].function_config.service_config[0].environment_variables.RULES).columnsToPseudonymizeIfPresent, "wrong_optional_field")
  }
}

run "other_rule_types_preserved" {
  command = plan

  # Test that other rule types are also preserved
  assert {
    error_message = "columnsToPseudonymize should be preserved"
    condition     = contains(keys(yamldecode(module.bulk_connector["test-hris"].function_config.service_config[0].environment_variables.RULES)), "columnsToPseudonymize")
  }

  assert {
    error_message = "columnsToDuplicate should be preserved"
    condition     = contains(keys(yamldecode(module.bulk_connector["test-hris"].function_config.service_config[0].environment_variables.RULES)), "columnsToDuplicate")
  }

  assert {
    error_message = "columnsToRedact should be preserved"
    condition     = contains(keys(yamldecode(module.bulk_connector["test-hris"].function_config.service_config[0].environment_variables.RULES)), "columnsToRedact")
  }

  assert {
    error_message = "columnsToRename should be preserved"
    condition     = contains(keys(yamldecode(module.bulk_connector["test-hris"].function_config.service_config[0].environment_variables.RULES)), "columnsToRename")
  }

  assert {
    error_message = "pseudonymFormat should be preserved"
    condition     = contains(keys(yamldecode(module.bulk_connector["test-hris"].function_config.service_config[0].environment_variables.RULES)), "pseudonymFormat")
  }
}

run "rules_precedence_test" {
  command = plan

  # Test that custom_bulk_connector_rules takes precedence over bulk_connectors rules (as intended)
  # The test-hris should use "wrong_field" from custom_bulk_connector_rules, not "employee_id" from bulk_connectors
  assert {
    error_message = "Should use rules from custom_bulk_connector_rules (which takes precedence)"
    condition     = contains(yamldecode(module.bulk_connector["test-hris"].function_config.service_config[0].environment_variables.RULES).columnsToPseudonymize, "wrong_field")
  }

  assert {
    error_message = "Should NOT use rules from bulk_connectors when custom_bulk_connector_rules is present"
    condition     = !contains(yamldecode(module.bulk_connector["test-hris"].function_config.service_config[0].environment_variables.RULES).columnsToPseudonymize, "employee_id")
  }
}

run "fallback_behavior" {
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

  # Test that custom_bulk_connector_rules is used as fallback
  assert {
    error_message = "Should fall back to custom_bulk_connector_rules when bulk_connectors.rules is null"
    condition     = can(yamldecode(module.bulk_connector["test-fallback"].function_config.service_config[0].environment_variables.RULES))
  }

  assert {
    error_message = "Fallback should include columnsToPseudonymizeIfPresent from custom_bulk_connector_rules"
    condition     = contains(keys(yamldecode(module.bulk_connector["test-fallback"].function_config.service_config[0].environment_variables.RULES)), "columnsToPseudonymizeIfPresent")
  }
}