# Plan succeeds when testing IAM/bucket policies are disabled and testing role ARNs are unset.

variables {
  environment_name     = "test"
  instance_id          = "hris"
  aws_account_id       = "123456789012"
  path_to_function_zip             = "../aws-proxy-lambda/tests/deployment.zip"
  function_zip_hash                = "dummy-hash-for-test"
  path_to_instance_ssm_parameters  = "PSOXY_TEST_HRIS_"
  provision_iam_policy_for_testing      = false
  aws_principal_arn_when_testing        = null
  aws_write_role_to_assume_when_testing = null
}

mock_provider "aws" {
  mock_data "aws_region" {
    defaults = {
      name = "us-east-1"
    }
  }
}

run "plan_without_testing_infra" {
  command = plan

  assert {
    error_message = "testing input bucket policy should not be created when provision_iam_policy_for_testing is false"
    condition     = length(aws_s3_bucket_policy.testing_input) == 0
  }

  assert {
    error_message = "testing sanitized bucket policy should not be created when provision_iam_policy_for_testing is false"
    condition     = length(aws_s3_bucket_policy.testing_sanitized) == 0
  }
}
