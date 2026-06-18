# aws-host should plan cleanly with provision_testing_infra=false (no test bucket policies / principals).

variables {
  aws_account_id          = "123456789012"
  environment_name        = "test"
  psoxy_base_dir          = "../../../"
  deployment_bundle       = "../aws-proxy-lambda/tests/deployment.zip"
  deployment_bundle_hash  = "dummy-hash-for-test"
  provision_testing_infra = false
  install_test_tool       = false

  api_connectors = {}
  bulk_connectors = {
    "test-hris" = {
      source_kind = "hris"
    }
  }
  webhook_collectors = {}
}

mock_provider "aws" {
  mock_data "aws_region" {
    defaults = {
      name = "us-east-1"
    }
  }

  mock_data "aws_caller_identity" {
    defaults = {
      account_id = "123456789012"
      arn        = "arn:aws:iam::123456789012:user/terraform"
      user_id    = "AIDATEST"
    }
  }

  mock_data "aws_iam_session_context" {
    defaults = {
      issuer_arn = "arn:aws:iam::123456789012:user/terraform"
    }
  }
}

run "plan_without_provision_testing_infra" {
  command = plan

  assert {
    error_message = "bulk connector should be provisioned through aws-proxy-bulk"
    condition     = length(module.bulk_connector) == 1
  }
}
