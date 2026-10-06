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

run "disabled_omits_bedrock_iam" {
  command = plan

  variables {
    enable_bedrock = false
  }

  assert {
    error_message = "enable_bedrock=false should not add InvokeBedrockForGenMetadata"
    condition     = length([for s in jsondecode(aws_iam_policy.required_resource_access.policy).Statement : s if s.Sid == "InvokeBedrockForGenMetadata"]) == 0
  }

  assert {
    error_message = "enable_bedrock=false should not set GEN_METADATA_BACKEND"
    condition     = !contains(keys(aws_lambda_function.instance.environment[0].variables), "GEN_METADATA_BACKEND")
  }
}

run "enabled_adds_bedrock_iam_and_backend_env" {
  command = plan

  variables {
    enable_bedrock = true
  }

  assert {
    error_message = "enable_bedrock=true should add InvokeBedrockForGenMetadata with InvokeModel/Converse"
    condition = toset(one([for s in jsondecode(aws_iam_policy.required_resource_access.policy).Statement : s if s.Sid == "InvokeBedrockForGenMetadata"]).Action) == toset([
      "bedrock:InvokeModel",
      "bedrock:Converse",
      "bedrock:InvokeModelWithResponseStream",
      "bedrock:ConverseStream",
    ])
  }

  assert {
    error_message = "enable_bedrock=true should set GEN_METADATA_BACKEND=bedrock"
    condition     = aws_lambda_function.instance.environment[0].variables["GEN_METADATA_BACKEND"] == "bedrock"
  }
}
