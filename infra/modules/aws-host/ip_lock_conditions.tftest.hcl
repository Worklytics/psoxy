variables {
  aws_account_id     = "123456789012"
  environment_name   = "test-ip-lock"
  psoxy_base_dir     = "../../../"
  caller_aws_arns    = ["arn:aws:iam::123456789012:role/test-caller"]
  api_connectors     = {}
  bulk_connectors    = {}
  webhook_collectors = {}

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

run "setup" {
  command = plan
}

run "validate_api_caller_ip_lock_passthrough" {
  command = plan

  assert {
    error_message = "aws-host should pass allowed_data_access_ip_blocks to the core AWS module api-caller role policy"
    condition     = strcontains(run.setup.module.psoxy.aws_iam_role.api-caller.assume_role_policy, "192.168.0.0/24")
  }
}
