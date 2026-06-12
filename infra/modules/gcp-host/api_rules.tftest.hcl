
# Test API connector rules processing in gcp-host module.

variables {
  gcp_project_id       = "test-project-123456"
  environment_name     = "test"
  worklytics_sa_emails = ["test@example.com"]
  psoxy_base_dir       = "../../../"

  api_connectors = {
    "test-api" = {
      source_kind          = "calendar"
      source_auth_strategy = "oauth2"
      target_host          = "www.googleapis.com"
      rules_file           = "tests/fixtures/calendar.yaml"
    }
  }

  custom_api_connector_rules = {
    "test-api" = "tests/fixtures/custom-api-rules.yaml"
  }

  bulk_connectors    = {}
  webhook_collectors = {}
}

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

run "decode_api_rules" {
  command = plan
  module {
    source = "./tests/decode_rules"
  }
  variables {
    encoded = run.setup.api_connector["test-api"].function_config.service_config[0].environment_variables.RULES
  }
}

run "custom_api_rules_override_connector_rules_file" {
  command = plan

  assert {
    error_message = "custom_api_connector_rules should override api_connectors.rules_file"
    condition     = run.decode_api_rules.decoded.endpoints[0].pathRegex == "/custom-api-rules"
  }
}
