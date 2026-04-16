variables {
  project_id                    = "test-project"
  environment_id_prefix         = "dev-"
  instance_id                   = "test-instance"
  service_account_email         = "test@example.com"
  artifacts_bucket_name         = "test-bucket"
  deployment_bundle_object_name = "bundle.zip"
  builder_sa_id                 = "projects/test-project/serviceAccounts/builder@example.com"
  source_kind                   = "test"

  allowed_webhook_ip_blocks = ["10.0.0.0/16"]
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

run "validate_cloud_run_webhook_invokers_condition" {
  command = plan

  assert {
    error_message = "IAM condition for run.invoker should enforce the IP address constraints from webhook IPs."
    condition     = length(google_cloud_run_service_iam_binding.invokers.condition) > 0 && strcontains(google_cloud_run_service_iam_binding.invokers.condition[0].expression, "10.0.0.0/16")
  }
}
