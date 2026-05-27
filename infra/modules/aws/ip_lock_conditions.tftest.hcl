variables {
  deployment_id   = "test-ip-lock"
  aws_account_id  = "123456789012"
  region          = "us-east-1"
  psoxy_base_dir  = "../../../"
  caller_aws_arns = ["arn:aws:iam::123456789012:role/test-caller"]

  allowed_data_access_ip_blocks = ["192.168.0.0/24"]
  allowed_webhook_ip_blocks     = ["10.0.0.0/16"]
}

mock_provider "aws" {
  mock_data "aws_region" {
    defaults = {
      name = "us-east-1"
    }
  }
}

run "validate_api_caller_ip_lock" {
  command = plan

  assert {
    error_message = "api-caller role should contain aws:SourceIp condition evaluated against allowed_data_access_ip_blocks"
    condition     = strcontains(aws_iam_role.api-caller.assume_role_policy, "192.168.0.0/24")
  }
}

run "validate_webhook_test_caller_ip_lock" {
  command = plan

  variables {
    enable_webhook_testing = true
  }

  assert {
    error_message = "webhook-test-caller role should contain aws:SourceIp condition evaluated against allowed_webhook_ip_blocks"
    condition     = strcontains(aws_iam_role.webhook-test-caller[0].assume_role_policy, "10.0.0.0/16")
  }
}
