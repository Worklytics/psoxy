# EventBridge trigger is selected when file_pattern is set (mutually exclusive with prefix/suffix).

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

run "plan_eventbridge_trigger" {
  command = plan

  variables {
    file_pattern = "exports/*/psoxy/*.csv"
  }

  assert {
    condition     = aws_s3_bucket_notification.bucket_notification.eventbridge == true
    error_message = "S3 bucket notification should enable EventBridge when file_pattern is set"
  }

  assert {
    condition     = length(aws_s3_bucket_notification.bucket_notification.lambda_function) == 0
    error_message = "direct S3→Lambda notification should not be configured when file_pattern is set"
  }

  assert {
    condition     = length(aws_cloudwatch_event_rule.bulk_s3_object_created) == 1
    error_message = "EventBridge rule should be created when file_pattern is set"
  }

  assert {
    condition     = length(aws_cloudwatch_event_target.bulk_s3_lambda) == 1
    error_message = "EventBridge target should invoke the bulk Lambda when file_pattern is set"
  }

  assert {
    condition     = length(aws_lambda_permission.allow_eventbridge) == 1
    error_message = "Lambda should grant invoke permission to EventBridge when file_pattern is set"
  }

  assert {
    condition     = length(aws_lambda_permission.allow_input_bucket) == 0
    error_message = "Lambda should not grant S3 invoke permission when file_pattern is set"
  }
}

run "plan_rejects_pattern_with_prefix" {
  command = plan

  variables {
    file_pattern = "exports/*.csv"
    file_prefix  = "exports/"
  }

  expect_failures = [
    var.file_pattern,
  ]
}
