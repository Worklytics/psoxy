# S3 bucket notification prefix/suffix filters are passed through to the Lambda trigger.

variables {
  environment_name                = "test"
  instance_id                     = "hris"
  aws_account_id                  = "123456789012"
  path_to_function_zip            = "../aws-proxy-lambda/tests/deployment.zip"
  function_zip_hash               = "dummy-hash-for-test"
  path_to_instance_ssm_parameters = "PSOXY_TEST_HRIS_"
}

mock_provider "aws" {
  mock_data "aws_region" {
    defaults = {
      name = "us-east-1"
    }
  }
}

run "plan_without_filters" {
  command = plan

  assert {
    condition     = aws_s3_bucket_notification.bucket_notification.lambda_function[0].filter_prefix == ""
    error_message = "filter_prefix should be empty when file_prefix is not set"
  }

  assert {
    condition     = aws_s3_bucket_notification.bucket_notification.lambda_function[0].filter_suffix == ""
    error_message = "filter_suffix should be empty when file_suffix is not set"
  }
}

run "plan_with_filters" {
  command = plan

  variables {
    file_prefix = "incoming/worklytics/"
    file_suffix = ".csv"
  }

  assert {
    condition     = aws_s3_bucket_notification.bucket_notification.lambda_function[0].filter_prefix == "incoming/worklytics/"
    error_message = "filter_prefix should match file_prefix"
  }

  assert {
    condition     = aws_s3_bucket_notification.bucket_notification.lambda_function[0].filter_suffix == ".csv"
    error_message = "filter_suffix should match file_suffix"
  }
}
