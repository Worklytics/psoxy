variables {
  project_id                    = "test-project"
  environment_id_prefix         = "dev-"
  instance_id                   = "test-instance"
  service_account_email         = "test@example.com"
  artifacts_bucket_name         = "test-bucket"
  deployment_bundle_object_name = "bundle.zip"
  builder_sa_id                 = "projects/test-project/serviceAccounts/builder@example.com"
  source_kind                   = "test"

  allowed_data_access_ip_blocks = ["192.168.0.0/24"]
}

mock_provider "google" {
  mock_data "google_project" {
    defaults = {
      project_id = "test-project"
      number     = "123456789"
    }
  }

  mock_data "google_service_account" {
    defaults = {
      account_id = "test@example.com"
      id         = "projects/test-project/serviceAccounts/test@example.com"
    }
  }
}

run "validate_cloud_run_invokers_no_ip_condition" {
  command = plan

  assert {
    error_message = "Cloud Run invoker must not use IAM IP conditions (unsupported); IP lock is app-level via ALLOWED_DATA_ACCESS_IP_BLOCKS."
    condition     = length(google_cloud_run_service_iam_binding.invokers.condition) == 0
  }
}
