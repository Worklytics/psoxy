# Worklytics tenant SAs need roles/run.invoker on API connectors (direct *.run.app and external ALB).

variables {
  gcp_project = {
    project_id = "test-project"
    number     = 123456789
  }
  environment_id_prefix         = "dev-"
  instance_id                   = "test-instance"
  config_parameter_prefix       = "TEST_"
  service_account_email         = "testsa@test-project.iam.gserviceaccount.com"
  artifacts_bucket_name         = "test-bucket"
  deployment_bundle_object_name = "bundle.zip"
  builder_sa_id                 = "projects/test-project/serviceAccounts/builder@test-project.iam.gserviceaccount.com"
  source_kind                   = "test"
  tf_runner_iam_principal       = "user:terraform@example.com"
  invoker_sa_emails             = ["worklytics-tenant@test-project.iam.gserviceaccount.com"]
}

mock_provider "google" {
  mock_data "google_service_account" {
    defaults = {
      account_id = "test@example.com"
      id         = "projects/test-project/serviceAccounts/test@example.com"
    }
  }
}

run "direct_run_app_grants_worklytics_sa_invoker" {
  command = plan

  assert {
    error_message = "direct *.run.app should grant roles/run.invoker to the Worklytics tenant SA"
    condition     = contains(google_cloud_run_service_iam_binding.invokers.members, "serviceAccount:worklytics-tenant@test-project.iam.gserviceaccount.com")
  }
}

run "external_alb_grants_worklytics_sa_invoker" {
  command = plan

  variables {
    external_lb_base_url = "https://proxy.example.com"
  }

  assert {
    error_message = "external ALB should still grant roles/run.invoker to the Worklytics tenant SA"
    condition     = contains(google_cloud_run_service_iam_binding.invokers.members, "serviceAccount:worklytics-tenant@test-project.iam.gserviceaccount.com")
  }
}
