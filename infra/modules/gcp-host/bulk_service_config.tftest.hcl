
# Test bulk connector memory/CPU defaults in gcp-host module

variables {
  gcp_project_id       = "test-project-123456"
  environment_name     = "test"
  worklytics_sa_emails = ["test@example.com"]
  psoxy_base_dir       = "../../../"

  bulk_connectors = {
    "default-bulk" = {
      source_kind = "hris"
      rules = {
        columnsToPseudonymize = ["employee_id"]
      }
    }
    "high-memory" = {
      source_kind         = "hris"
      available_memory_mb = 4096
      rules = {
        columnsToPseudonymize = ["employee_id"]
      }
    }
    "custom-memory" = {
      source_kind         = "hris"
      available_memory_mb = 2048
      rules = {
        columnsToPseudonymize = ["employee_id"]
      }
    }
  }

  custom_bulk_connector_arguments = {
    "args-override" = {
      available_memory_mb = 512
    }
  }

  api_connectors     = {}
  webhook_collectors = {}
}

mock_provider "google" {
  mock_data "google_project" {
    defaults = {
      project_id = "test-project-123456"
      number     = 123456789
    }
  }

  mock_data "google_compute_default_service_account" {
    defaults = {
      email = "123456789-compute@developer.gserviceaccount.com"
      name  = "projects/test-project-123456/serviceAccounts/123456789-compute@developer.gserviceaccount.com"
    }
  }
}

run "setup" {
  command = plan

  variables {
    bulk_connectors = merge(var.bulk_connectors, {
      "args-override" = {
        source_kind = "hris"
        rules = {
          columnsToPseudonymize = ["employee_id"]
        }
      }
    })
  }
}

run "default_memory_and_cpu" {
  command = plan

  assert {
    error_message = "Default bulk memory should be 1024M"
    condition     = run.setup.bulk_connector["default-bulk"].function_config.service_config[0].available_memory == "1024M"
  }

  assert {
    error_message = "Default bulk CPU should be 0.5 for 1024M memory"
    condition     = run.setup.bulk_connector["default-bulk"].function_config.service_config[0].available_cpu == "0.5"
  }
}

run "auto_cpu_for_high_memory" {
  command = plan

  assert {
    error_message = "4096M memory should auto-select 1 CPU"
    condition     = run.setup.bulk_connector["high-memory"].function_config.service_config[0].available_cpu == "1"
  }
}

run "custom_memory_with_auto_cpu" {
  command = plan

  assert {
    error_message = "Configured memory should be applied"
    condition     = run.setup.bulk_connector["custom-memory"].function_config.service_config[0].available_memory == "2048M"
  }

  assert {
    error_message = "2048M memory should auto-select 1 CPU"
    condition     = run.setup.bulk_connector["custom-memory"].function_config.service_config[0].available_cpu == "1"
  }
}

run "custom_bulk_connector_arguments_override" {
  command = plan

  assert {
    error_message = "custom_bulk_connector_arguments should override connector memory"
    condition     = run.setup.bulk_connector["args-override"].function_config.service_config[0].available_memory == "512M"
  }

  assert {
    error_message = "512M memory should auto-select 0.333 CPU"
    condition     = run.setup.bulk_connector["args-override"].function_config.service_config[0].available_cpu == "0.333"
  }
}
