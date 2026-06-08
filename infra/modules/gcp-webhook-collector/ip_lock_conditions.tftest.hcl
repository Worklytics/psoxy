variables {
  project_id              = "test-project"
  environment_id_prefix   = "dev-"
  instance_id             = "test-instance"
  config_parameter_prefix = "TEST_"
  service_account = {
    service_account_id = "projects/test-project/serviceAccounts/testsa@test-project.iam.gserviceaccount.com"
    email              = "testsa@test-project.iam.gserviceaccount.com"
  }
  tf_runner_iam_principal        = "user:terraform@example.com"
  artifacts_bucket_name          = "test-bucket"
  deployment_bundle_object_name  = "bundle.zip"
  builder_sa_id                  = "projects/test-project/serviceAccounts/builder@test-project.iam.gserviceaccount.com"
  source_kind                    = "test"
  rules_file                     = "README.md"
  oidc_token_verifier_role_id    = "projects/test-project/roles/oidcTokenVerifier"
  bucket_write_role_id           = "roles/storage.objectCreator"
  webhook_batch_invoker_sa_email = "batch@test-project.iam.gserviceaccount.com"
  key_ring_id                    = "projects/test-project/locations/us-central1/keyRings/test-key-ring"
  provision_auth_key             = {}
  example_identity               = "test-user@example.com"

  allowed_webhook_ip_blocks = ["10.0.0.0/16"]
}

mock_provider "google" {
  mock_data "google_project" {
    defaults = {
      project_id = "test-project"
      number     = "123456789"
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
    condition     = google_service_account_iam_member.tf_runner_act_as.service_account_id == "projects/test-project/serviceAccounts/testsa@test-project.iam.gserviceaccount.com"
  }
}
