variables {
  environment_name       = "test-remote-resources"
  instance_id            = "gcal"
  path_to_function_zip   = "s3://deployment-bucket/psoxy.zip"
  function_zip_hash      = "fake-hash"
  remote_resource_bucket = "remote-resource-bucket"

  remote_resource_instance_path = "psoxy-dev/GCAL/"
  remote_resource_shared_path   = "psoxy-dev/"
}

mock_provider "aws" {
  mock_data "aws_region" {
    defaults = {
      id   = "us-east-1"
      name = "us-east-1"
    }
  }

  mock_data "aws_caller_identity" {
    defaults = {
      account_id = "123456789012"
    }
  }
}

run "remote_resource_iam_paths_match_slash_terminated_prefixes" {
  command = plan

  assert {
    error_message = "Remote resource IAM policy should allow object ARNs below slash-terminated prefixes without introducing double slashes."
    condition = (
      contains(
        one([for statement in jsondecode(aws_iam_policy.required_resource_access.policy).Statement : statement if statement.Sid == "ReadRemoteResourceBucket"]).Resource,
        "arn:aws:s3:::remote-resource-bucket/psoxy-dev/GCAL/*"
      )
      && contains(
        one([for statement in jsondecode(aws_iam_policy.required_resource_access.policy).Statement : statement if statement.Sid == "ReadRemoteResourceBucket"]).Resource,
        "arn:aws:s3:::remote-resource-bucket/psoxy-dev/*"
      )
      && !contains(
        one([for statement in jsondecode(aws_iam_policy.required_resource_access.policy).Statement : statement if statement.Sid == "ReadRemoteResourceBucket"]).Resource,
        "arn:aws:s3:::remote-resource-bucket/psoxy-dev/GCAL//*"
      )
    )
  }
}
