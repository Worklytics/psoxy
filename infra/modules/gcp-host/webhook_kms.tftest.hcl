# Key ring creation must follow Cloud KMS API enablement, and only when a webhook provisions a key.

variables {
  gcp_project_id       = "test-project-123456"
  environment_name     = "test"
  worklytics_sa_emails = ["test@example.com"]
  psoxy_base_dir       = "../../../"

  api_connectors  = {}
  bulk_connectors = {}

  webhook_collectors = {
    "llm-portal" = {
      rules_file = "tests/fixtures/calendar.yaml"
      provision_auth_key = {
        rotation_days = 2
      }
      example_identity = "user@example.com"
    }
  }
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

run "provision_auth_key_enables_kms_and_creates_key_ring" {
  command = plan

  # Service id is computed; pin it so the plan-time output is known.
  override_resource {
    target = module.psoxy.google_project_service.cloud_kms[0]
    values = {
      id = "test-project-123456/cloudkms.googleapis.com"
    }
    override_during = plan
  }

  assert {
    error_message = "Cloud KMS API should be enabled when a webhook provisions an auth key."
    condition     = module.psoxy.kms_api_enabled == "test-project-123456/cloudkms.googleapis.com"
  }

  assert {
    error_message = "A key ring should be created when kms_key_ring is omitted."
    condition     = length(google_kms_key_ring.proxy_key_ring) == 1
  }
}

run "byo_key_ring_skips_key_ring_but_enables_kms" {
  command = plan

  variables {
    kms_key_ring = "projects/test-project-123456/locations/us-central1/keyRings/existing"
  }

  override_resource {
    target = module.psoxy.google_project_service.cloud_kms[0]
    values = {
      id = "test-project-123456/cloudkms.googleapis.com"
    }
    override_during = plan
  }

  assert {
    error_message = "Cloud KMS API is still required to create a crypto key on a customer-supplied key ring."
    condition     = module.psoxy.kms_api_enabled == "test-project-123456/cloudkms.googleapis.com"
  }

  assert {
    error_message = "A customer-supplied key ring must not be recreated."
    condition     = length(google_kms_key_ring.proxy_key_ring) == 0
  }
}

run "webhook_without_auth_key_does_not_enable_kms" {
  command = plan

  variables {
    webhook_collectors = {
      "llm-portal" = {
        rules_file       = "tests/fixtures/calendar.yaml"
        example_identity = "user@example.com"
      }
    }
  }

  assert {
    error_message = "Cloud KMS API should stay disabled when no webhook provisions an auth key."
    condition     = module.psoxy.kms_api_enabled == null
  }

  assert {
    error_message = "No key ring should be created when no webhook provisions an auth key."
    condition     = length(google_kms_key_ring.proxy_key_ring) == 0
  }
}
