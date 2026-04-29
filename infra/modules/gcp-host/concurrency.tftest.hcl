
# Test for concurrency settings passthrough in gcp-host module
# Verifies that api_connector_instance_concurrency and max_instances_per_api_connector
# are properly passed from gcp-host variables into the gcp-proxy-api service_config.

variables {
  gcp_project_id       = "test-project-123456"
  environment_name     = "test"
  worklytics_sa_emails = ["test@example.com"]
  psoxy_base_dir       = "../../../" # Use actual path for validation

  api_connectors = {
    "test-gmail" = {
      source_kind          = "gmail"
      source_auth_strategy = "gcp-sa"
      target_host          = "gmail.googleapis.com"
      example_api_calls    = ["/gmail/v1/users/me/messages"]
    }
  }

  bulk_connectors    = {}
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

run "default_concurrency_settings" {
  command = plan

  # Test that default instance_concurrency (5) is passed through
  assert {
    error_message = "Default instance_concurrency should be 5"
    condition     = module.api_connector["test-gmail"].function_config.service_config[0].max_instance_request_concurrency == 5
  }

  # Test that default max_instance_count (20) is passed through
  assert {
    error_message = "Default max_instance_count should be 20"
    condition     = module.api_connector["test-gmail"].function_config.service_config[0].max_instance_count == 20
  }

  # Test that CPU is set to "1" when concurrency > 1
  assert {
    error_message = "available_cpu should be '1' when concurrency > 1"
    condition     = module.api_connector["test-gmail"].function_config.service_config[0].available_cpu == "1"
  }
}

run "custom_concurrency_settings" {
  command = plan

  variables {
    api_connector_instance_concurrency = 3
    max_instances_per_api_connector    = 10
  }

  assert {
    error_message = "instance_concurrency should be overridden to 3"
    condition     = module.api_connector["test-gmail"].function_config.service_config[0].max_instance_request_concurrency == 3
  }

  assert {
    error_message = "max_instance_count should be overridden to 10"
    condition     = module.api_connector["test-gmail"].function_config.service_config[0].max_instance_count == 10
  }
}

run "single_concurrency_disables_cpu_allocation" {
  command = plan

  variables {
    api_connector_instance_concurrency = 1
  }

  # When concurrency is 1, available_cpu should be null (not explicitly allocated)
  assert {
    error_message = "available_cpu should be null when concurrency = 1"
    condition     = module.api_connector["test-gmail"].function_config.service_config[0].available_cpu == null
  }

  assert {
    error_message = "instance_concurrency should be 1"
    condition     = module.api_connector["test-gmail"].function_config.service_config[0].max_instance_request_concurrency == 1
  }
}
