# Plan-time checks for SSM tier selection and remote bucket fallback.

mock_provider "aws" {}

run "standard_tier_for_small_rules" {
  command = plan

  module {
    source = "./"
  }

  variables {
    prefix  = "TEST_"
    content = "columnsToRedact: []"
  }

  assert {
    condition     = length(aws_ssm_parameter.rules) == 1 && aws_ssm_parameter.rules[0].tier == "Standard"
    error_message = "Small rules should be stored as a Standard SSM parameter"
  }

  assert {
    condition     = length(aws_s3_object.rules) == 0
    error_message = "Small rules should not be uploaded to S3"
  }
}

run "advanced_tier_for_medium_rules" {
  command = plan

  module {
    source = "./"
  }

  variables {
    prefix  = "TEST_"
    content = join("", [for _ in range(0, 5000, 5) : "abcde"])
  }

  assert {
    condition     = length(aws_ssm_parameter.rules) == 1 && aws_ssm_parameter.rules[0].tier == "Advanced"
    error_message = "Medium rules should use the Advanced SSM parameter tier"
  }
}

run "remote_bucket_when_rules_exceed_ssm_limit" {
  command = plan

  module {
    source = "./"
  }

  variables {
    prefix                        = "TEST_"
    file_path                     = "tests/rules-over-ssm-limit.yaml"
    remote_resource_bucket        = "my-artifacts"
    remote_resource_instance_path = "psoxy-dev-FOO/"
  }

  assert {
    condition     = length(aws_ssm_parameter.rules) == 0
    error_message = "Oversized rules should not be stored in SSM"
  }

  assert {
    condition     = length(aws_s3_object.rules) == 1 && aws_s3_object.rules[0].key == "psoxy-dev-FOO/rules.yaml"
    error_message = "Oversized rules should be uploaded to the instance resource path as rules.yaml"
  }
}

run "accept_yml_rules_file" {
  command = plan

  module {
    source = "./"
  }

  variables {
    prefix    = "TEST_"
    file_path = "tests/rules-small.yml"
  }

  assert {
    condition     = length(aws_ssm_parameter.rules) == 1
    error_message = "Rules from a .yml file should be stored in SSM"
  }
}

run "remote_bucket_key_at_bucket_root" {
  command = plan

  module {
    source = "./"
  }

  variables {
    prefix                        = "TEST_"
    file_path                     = "tests/rules-over-ssm-limit.yaml"
    remote_resource_bucket        = "my-artifacts"
    remote_resource_instance_path = "/"
  }

  assert {
    condition     = length(aws_s3_object.rules) == 1 && aws_s3_object.rules[0].key == "rules.yaml"
    error_message = "Bucket-root instance path should upload rules.yaml without a leading slash"
  }
}
