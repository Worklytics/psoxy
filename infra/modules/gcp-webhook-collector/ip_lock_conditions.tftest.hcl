variables {
  project_id                    = "test-project"
  environment_id_prefix         = "dev-"
  instance_id                   = "test-instance"
  service_account_email         = "test@example.com"
  tf_runner_iam_principal       = "user:terraform@example.com"
  artifacts_bucket_name         = "test-bucket"
  deployment_bundle_object_name = "bundle.zip"
  builder_sa_id                 = "projects/test-project/serviceAccounts/builder@example.com"
  source_kind                   = "test"
  rules_file                    = "README.md"
  oidc_token_verifier_role_id   = "projects/test-project/roles/oidcTokenVerifier"

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

run "validate_cloud_run_webhook_invokers_no_ip_condition" {
  command = plan

  assert {
    error_message = "Cloud Run webhook invoker must not use IAM IP conditions (unsupported); IP lock is app-level via ALLOWED_WEBHOOK_IP_BLOCKS."
    condition     = length(google_cloud_run_service_iam_binding.invokers.condition) == 0
  }

  assert {
    error_message = "Terraform runner must be granted act-as on the full service account resource name."
    condition     = google_service_account_iam_member.tf_runner_act_as.service_account_id == "projects/test-project/serviceAccounts/test@example.com"
  }
}
