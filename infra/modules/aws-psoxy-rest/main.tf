# provision a Psoxy instance into AWS account

terraform {
  required_providers {
    aws = {
      version = "~> 4.12"
    }
  }
}

# NOTE: region used to be passed in as a variable; put it MUST match the region in which the lambda
# is provisioned, and that's implicit in the provider - so we should just infer from the provider
data "aws_region" "current" {}

locals {
  # from v0.5, these will be required; for now, allow `null` but filter out so taken from config yaml
  required_env_vars = { for k, v in {
    SOURCE                          = var.source_kind
    TARGET_HOST                     = var.target_host
    SOURCE_AUTH_STRATEGY_IDENTIFIER = var.source_auth_strategy
    OAUTH_SCOPES                    = join(" ", var.oauth_scopes)
    IDENTIFIER_SCOPE_ID             = var.identifier_scope_id
    }
    : k => v if v != null
  }

  arn_for_test_calls = var.api_caller_role_arn
}

module "psoxy_lambda" {
  source = "../aws-psoxy-lambda"

  function_name                   = var.function_name
  handler_class                   = "co.worklytics.psoxy.Handler"
  path_to_function_zip            = var.path_to_function_zip
  function_zip_hash               = var.function_zip_hash
  memory_size_mb                  = var.memory_size_mb
  timeout_seconds                 = 55
  reserved_concurrent_executions  = var.reserved_concurrent_executions
  path_to_config                  = var.path_to_config
  source_kind                     = var.source_kind
  function_parameters             = var.function_parameters
  path_to_instance_ssm_parameters = var.path_to_instance_ssm_parameters
  global_parameter_arns           = var.global_parameter_arns
  ssm_kms_key_ids                 = var.ssm_kms_key_ids
  log_retention_in_days           = var.log_retention_days

  environment_variables = merge(
    var.environment_variables,
    local.required_env_vars
  )
}

resource "aws_lambda_function_url" "lambda_url" {
  function_name      = var.function_name # woudld 'module.psoxy_lambda.function_name' avoid explicit dependency??
  authorization_type = "AWS_IAM"

  cors {
    allow_credentials = true
    allow_origins     = ["*"]
    allow_methods     = ["POST", "GET", "HEAD"] # TODO: configurable? not all require POST
    allow_headers     = ["date", "keep-alive"]
    expose_headers    = ["keep-alive", "date"]
    max_age           = 86400
  }

  depends_on = [
    module.psoxy_lambda
  ]
}

locals {
  # lambda_url has trailing /, but our example_api_calls already have preceding /
  proxy_endpoint_url  = substr(aws_lambda_function_url.lambda_url.function_url, 0, length(aws_lambda_function_url.lambda_url.function_url) - 1)
  impersonation_param = var.example_api_calls_user_to_impersonate == null ? "" : " -i \"${var.example_api_calls_user_to_impersonate}\""
  command_npm_install = "npm --prefix ${var.path_to_repo_root}tools/psoxy-test install"
  command_cli_call    = "node ${var.path_to_repo_root}tools/psoxy-test/cli-call.js -r \"${local.arn_for_test_calls}\""
  command_test_calls = [for path in var.example_api_calls :
    "${local.command_cli_call} -u \"${local.proxy_endpoint_url}${path}\"${local.impersonation_param}"
  ]
  command_test_logs = "node ${var.path_to_repo_root}tools/psoxy-test/cli-logs.js -r \"${local.arn_for_test_calls}\" -re \"${data.aws_region.current.id}\" -l \"${module.psoxy_lambda.log_group}\""

  awscurl_test_call = "${var.path_to_repo_root}tools/test-psoxy.sh -a -r \"${local.arn_for_test_calls}\" -e  \"${data.aws_region.current.id}\""
  awscurl_test_calls = [ for path in var.example_api_calls :
    "${local.awscurl_test_call} -u \"${local.proxy_endpoint_url}${path}\"${local.impersonation_param}"
  ]

  todo_content = <<EOT

## Testing ${var.function_name}

Review the deployed function in AWS console:

- https://console.aws.amazon.com/lambda/home?region=${data.aws_region.current.id}#/functions/${var.function_name}?tab=monitoring

We provide some Node.js scripts to simplify testing your proxy deployment. To be able run test
commands below, you will need
   - Node.js (>=16) and npm (v >=8) installed.
   - install the tool itself (in the location from which you plan to run the test commands, if it's
     not the same location where you originally ran the Terraform apply)

```shell
${local.command_npm_install}
```
   - ensure the location you're running from is authenticated as an AWS principal which can assume
     the role `${var.api_caller_role_arn}` ( `aws sts get-caller-identity` to determine who you're
     authenticated as; if necessary, add this ARN to the `caller_aws_arns` list in the
     `terraform.tfvars` file of your configuration to allow it to assume that role)

### Make "test calls"
First, run an initial "Health Check" call to make sure the Psoxy instance is up and running:

```shell
${local.command_cli_call} -u ${local.proxy_endpoint_url} --health-check
```

Then, based on your configuration, these are some example test calls you can try (YMMV):

```shell
${coalesce(join("\n", local.command_test_calls), "cd docs/example-api-calls/")}
```

Feel free to try the above calls, and reference to the source's API docs for other parameters /
endpoints to experiment with.


As an alternative, we offer a simpler bash script for testing that wraps `awscurl` + `jq`, if those
are installed on your system:
```shell
${coalesce(join("\n", local.awscurl_test_calls), "cd docs/example-api-calls/")}
```

### Check logs (AWS CloudWatch)

Based on your configuration, the following command allows you to inspect the
logs of your Psoxy deployment:

```shell
${local.command_test_logs}
```

---

Please, check the documentation of our [Psoxy Testing tools](${var.path_to_repo_root}tools/psoxy-test/README.md)
for a detailed description of all the different options.

Contact support@worklytics.co for assistance modifying the rules as needed.

EOT
}

resource "local_file" "todo" {
  filename = "TODO ${var.todo_step} - test ${var.function_name}.md"
  content  = local.todo_content
}

resource "local_file" "test_script" {
  filename        = "test-${var.function_name}.sh"
  file_permission = "0770"
  content         = <<EOT
#!/bin/bash
API_PATH=$${1:-${try(var.example_api_calls[0], "")}}
echo "Quick test of ${var.function_name} ..."

${local.command_cli_call} -u "${local.proxy_endpoint_url}" --health-check

${local.command_cli_call} -u "${local.proxy_endpoint_url}$API_PATH" ${local.impersonation_param}

echo "Invoke this script with any of the following as arguments to test other endpoints:${"\r\n\t"}${join("\r\n\t", var.example_api_calls)}"
EOT

}


output "endpoint_url" {
  value = aws_lambda_function_url.lambda_url.function_url
}

output "function_arn" {
  value = module.psoxy_lambda.function_arn
}

# assuredly unique within AWS account
output "function_name" {
  value = module.psoxy_lambda.function_name
}

output "instance_role_arn" {
  value = module.psoxy_lambda.iam_role_for_lambda_arn
}

output "instance_role_name" {
  value = module.psoxy_lambda.iam_role_for_lambda_name
}

# in practice, same as function_name; but for simplicity may want something specific to the deployment
output "instance_id" {
  value = module.psoxy_lambda.function_name
}

output "proxy_kind" {
  value       = "rest"
  description = "The kind of proxy instance this is."
}

output "todo" {
  value = local.todo_content
}

output "next_todo_step" {
  value = var.todo_step + 1
}
