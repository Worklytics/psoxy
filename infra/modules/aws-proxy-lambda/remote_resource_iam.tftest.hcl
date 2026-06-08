variables {
  environment_name     = "test"
  instance_id          = "connector"
  path_to_function_zip = "tests/deployment.zip"
  function_zip_hash    = "dummy-hash-for-test"
}

mock_provider "aws" {
  mock_data "aws_region" {
    defaults = {
      name = "us-east-1"
    }
  }
}

run "instance_path_strips_trailing_slash" {
  command = plan

  variables {
    remote_resource_bucket        = "my-artifacts"
    remote_resource_instance_path = "instances/foo/"
    remote_resource_shared_path   = null
  }

  assert {
    error_message = "instance path should normalize trailing slash in S3 object ARN"
    condition = contains(
      one([for s in jsondecode(aws_iam_policy.required_resource_access.policy).Statement : s.Resource if s.Sid == "ReadRemoteResourceBucket"]),
      "arn:aws:s3:::my-artifacts/instances/foo/*"
    )
  }
}

run "empty_instance_path_grants_bucket_root" {
  command = plan

  variables {
    remote_resource_bucket        = "my-artifacts"
    remote_resource_instance_path = ""
    remote_resource_shared_path   = null
  }

  assert {
    error_message = "empty instance path should grant bucket root objects"
    condition     = toset(one([for s in jsondecode(aws_iam_policy.required_resource_access.policy).Statement : s.Resource if s.Sid == "ReadRemoteResourceBucket"])) == toset(["arn:aws:s3:::my-artifacts/*"])
  }
}

run "no_paths_grants_bucket_root" {
  command = plan

  variables {
    remote_resource_bucket        = "my-artifacts"
    remote_resource_instance_path = null
    remote_resource_shared_path   = null
  }

  assert {
    error_message = "no path prefixes should grant bucket root objects"
    condition     = toset(one([for s in jsondecode(aws_iam_policy.required_resource_access.policy).Statement : s.Resource if s.Sid == "ReadRemoteResourceBucket"])) == toset(["arn:aws:s3:::my-artifacts/*"])
  }
}

run "instance_and_shared_paths_are_distinct" {
  command = plan

  variables {
    remote_resource_bucket        = "my-artifacts"
    remote_resource_instance_path = "instances/foo"
    remote_resource_shared_path   = "shared/models/"
  }

  assert {
    error_message = "instance and shared prefixes should produce distinct S3 object ARNs"
    condition = toset(one([for s in jsondecode(aws_iam_policy.required_resource_access.policy).Statement : s.Resource if s.Sid == "ReadRemoteResourceBucket"])) == toset([
      "arn:aws:s3:::my-artifacts/instances/foo/*",
      "arn:aws:s3:::my-artifacts/shared/models/*",
    ])
  }
}

run "duplicate_prefixes_dedupe_to_one_arn" {
  command = plan

  variables {
    remote_resource_bucket        = "my-artifacts"
    remote_resource_instance_path = "shared/"
    remote_resource_shared_path   = "shared"
  }

  assert {
    error_message = "identical instance and shared prefixes should dedupe to a single ARN"
    condition     = toset(one([for s in jsondecode(aws_iam_policy.required_resource_access.policy).Statement : s.Resource if s.Sid == "ReadRemoteResourceBucket"])) == toset(["arn:aws:s3:::my-artifacts/shared/*"])
  }
}
