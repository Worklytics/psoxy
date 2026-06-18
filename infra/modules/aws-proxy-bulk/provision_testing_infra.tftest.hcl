# Plan succeeds with testing IAM/bucket policies enabled and disabled.

variables {
  environment_name                  = "test"
  instance_id                       = "hris"
  aws_account_id                    = "123456789012"
  path_to_function_zip              = "../aws-proxy-lambda/tests/deployment.zip"
  function_zip_hash                 = "dummy-hash-for-test"
  path_to_instance_ssm_parameters   = "PSOXY_TEST_HRIS_"
  provision_iam_policy_for_testing  = false
  aws_principal_arn_when_testing    = null
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

run "plan_with_testing_infra" {
  command = plan

  variables {
    provision_iam_policy_for_testing      = true
    test_aws_principal_arns               = ["arn:aws:iam::123456789012:user/terraform"]
    aws_principal_arn_when_testing        = "arn:aws:iam::123456789012:role/testCaller"
    aws_write_role_to_assume_when_testing = "arn:aws:iam::123456789012:role/terraform"
  }

  assert {
    error_message = "testing input bucket policy should be created when provision_iam_policy_for_testing is true"
    condition     = length(aws_s3_bucket_policy.testing_input) == 1
  }

  assert {
    error_message = "testing sanitized bucket policy should be created when provision_iam_policy_for_testing is true"
    condition     = length(aws_s3_bucket_policy.testing_sanitized) == 1
  }
}
