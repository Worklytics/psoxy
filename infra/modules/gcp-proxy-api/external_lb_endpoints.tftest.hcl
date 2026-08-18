# When external_lb_base_url is set, TODOs and public endpoint outputs must use that host
# (not the Cloud Function *.run.app URI).

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
  example_api_calls             = ["/health"]
  todos_as_local_files          = false
}

mock_provider "google" {
  mock_data "google_service_account" {
    defaults = {
      account_id = "test@example.com"
      id         = "projects/test-project/serviceAccounts/test@example.com"
    }
  }
}

run "alb_domain_used_in_todos_and_outputs" {
  command = plan

  variables {
    external_lb_base_url = "https://proxy.example.com"
  }

  assert {
    error_message = "proxy_endpoint_url should be the ALB domain plus function name"
    condition     = output.proxy_endpoint_url == "https://proxy.example.com/dev-test-instance"
  }

  assert {
    error_message = "endpoint_url should be the ALB domain plus function name with trailing slash"
    condition     = output.endpoint_url == "https://proxy.example.com/dev-test-instance/"
  }

  assert {
    error_message = "test TODO content should call the ALB domain, not a Cloud Function URI"
    condition     = strcontains(output.todo, "https://proxy.example.com/dev-test-instance") && !strcontains(output.todo, ".run.app")
  }
}
