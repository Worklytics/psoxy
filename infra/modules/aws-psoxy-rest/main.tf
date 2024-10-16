# provision a Psoxy instance into AWS account

terraform {
  required_providers {
    aws = {
      version = ">= 4.12, < 5.0"
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

  # helper to clarify conditionals throughout
  use_api_gateway = var.api_gateway_v2 != null
}

module "psoxy_lambda" {
  source = "../aws-psoxy-lambda"

  environment_name                     = var.environment_name
  instance_id                          = var.instance_id
  handler_class                        = "co.worklytics.psoxy.Handler"
  path_to_function_zip                 = var.path_to_function_zip
  function_zip_hash                    = var.function_zip_hash
  function_env_kms_key_arn             = var.function_env_kms_key_arn
  logs_kms_key_arn                     = var.logs_kms_key_arn
  memory_size_mb                       = var.memory_size_mb
  timeout_seconds                      = 55
  reserved_concurrent_executions       = var.reserved_concurrent_executions
  path_to_config                       = var.path_to_config
  source_kind                          = var.source_kind
  function_parameters                  = var.function_parameters
  path_to_instance_ssm_parameters      = var.path_to_instance_ssm_parameters
  path_to_shared_ssm_parameters        = var.path_to_shared_ssm_parameters
  global_parameter_arns                = var.global_parameter_arns
  global_secrets_manager_secret_arns   = var.global_secrets_manager_secret_arns
  ssm_kms_key_ids                      = var.ssm_kms_key_ids
  log_retention_in_days                = var.log_retention_days
  vpc_config                           = var.vpc_config
  secrets_store_implementation         = var.secrets_store_implementation
  aws_lambda_execution_role_policy_arn = var.aws_lambda_execution_role_policy_arn
  iam_roles_permissions_boundary       = var.iam_roles_permissions_boundary


  environment_variables = merge(
    var.environment_variables,
    local.required_env_vars
  )
}

# lambda function URL (only if NOT using API Gateway)
resource "aws_lambda_function_url" "lambda_url" {
  count = local.use_api_gateway ? 0 : 1

  function_name      = module.psoxy_lambda.function_name
  authorization_type = "AWS_IAM"

  depends_on = [
    module.psoxy_lambda
  ]
}

# API Gateway (only if NOT using lambda function URL)
resource "aws_apigatewayv2_integration" "map" {
  count = local.use_api_gateway ? 1 : 0

  api_id                 = var.api_gateway_v2.id
  integration_type       = "AWS_PROXY"
  connection_type        = "INTERNET"
  integration_method     = "POST"
  integration_uri        = module.psoxy_lambda.function_arn
  payload_format_version = "2.0"
  timeout_milliseconds   = 30000 # ideally would be 55 or 60, but docs say limit is 30s
}

resource "aws_apigatewayv2_route" "methods" {
  for_each = toset(local.use_api_gateway ? var.http_methods : [])

  api_id             = var.api_gateway_v2.id
  route_key          = "${each.key} /${module.psoxy_lambda.function_name}/{proxy+}"
  authorization_type = "AWS_IAM"
  target             = "integrations/${aws_apigatewayv2_integration.map[0].id}"
}

resource "aws_lambda_permission" "api_gateway" {
  count = local.use_api_gateway ? 1 : 0

  statement_id  = "Allow${module.psoxy_lambda.function_name}Invoke"
  action        = "lambda:InvokeFunction"
  function_name = module.psoxy_lambda.function_name
  principal     = "apigateway.amazonaws.com"


  # The /*/*/ part allows invocation from any stage, method and resource path
  # within API Gateway REST API.
  # TODO: limit by http method here too?
  source_arn = "${var.api_gateway_v2.execution_arn}/*/*/${module.psoxy_lambda.function_name}/{proxy+}"
}


locals {
  api_gateway_url = local.use_api_gateway ? "${var.api_gateway_v2.stage_invoke_url}/${module.psoxy_lambda.function_name}" : null

  # lambda_url has trailing /, but our example_api_calls already have preceding /
  function_url = local.use_api_gateway ? null : substr(aws_lambda_function_url.lambda_url[0].function_url, 0, length(aws_lambda_function_url.lambda_url[0].function_url) - 1)

  proxy_endpoint_url = coalesce(local.api_gateway_url, local.function_url)

  impersonation_param = var.example_api_calls_user_to_impersonate == null ? "" : " -i \"${var.example_api_calls_user_to_impersonate}\""

  # don't want to *require* assumption of a role for testing; while we expect it in usual case
  # (a provisioner must assume PsoxyCaller role for the test), customer could be using a single
  # admin user for everything such that it's not required
  role_param = local.arn_for_test_calls == null ? "" : " -r \"${local.arn_for_test_calls}\""

  command_npm_install = "npm --prefix ${var.path_to_repo_root}tools/psoxy-test install"
  command_cli_call    = "node ${var.path_to_repo_root}tools/psoxy-test/cli-call.js ${local.role_param} -re \"${data.aws_region.current.id}\""
  command_test_calls = [for path in var.example_api_calls :
    "${local.command_cli_call} -u \"${local.proxy_endpoint_url}${path}\"${local.impersonation_param}"
  ]
  command_test_logs = "node ${var.path_to_repo_root}tools/psoxy-test/cli-logs.js ${local.role_param} -re \"${data.aws_region.current.id}\" -l \"${module.psoxy_lambda.log_group}\""

  awscurl_test_call = "${var.path_to_repo_root}tools/test-psoxy.sh -a ${local.role_param} -e \"${data.aws_region.current.id}\""
  awscurl_test_calls = [for path in var.example_api_calls :
    "${local.awscurl_test_call} -u \"${local.proxy_endpoint_url}${path}\"${local.impersonation_param}"
  ]

  todo_content = <<EOT

## Testing ${var.instance_id}

Review the deployed function in AWS console:

- https://console.aws.amazon.com/lambda/home?region=${data.aws_region.current.id}#/functions/${module.psoxy_lambda.function_name}?tab=monitoring

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
${local.command_cli_call} -u ${local.proxy_endpoint_url}/ --health-check
```

Then, based on your configuration, these are some example test calls you can try (YMMV):

```shell
${join("\n", local.command_test_calls)}
```

Feel free to try the above calls, and reference to the source's API docs for other parameters /
endpoints to experiment with.


As an alternative, we offer a simpler bash script for testing that wraps `awscurl` + `jq`, if those
are installed on your system:
```shell
${join("\n", local.awscurl_test_calls)}
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
  count = var.todos_as_local_files ? 1 : 0

  filename = "TODO ${var.todo_step} - test ${var.instance_id}.md"
  content  = local.todo_content
}

locals {
  test_script = <<EOT
#!/bin/bash
API_PATH=$${1:-${try(var.example_api_calls[0], "")}}
echo "Quick test of ${module.psoxy_lambda.function_name} ..."

${local.command_cli_call} -u "${local.proxy_endpoint_url}/" --health-check

${local.command_cli_call} -u "${local.proxy_endpoint_url}$API_PATH" ${local.impersonation_param}

echo "Invoke this script with any of the following as arguments to test other endpoints:${"\r\n\t"}${join("\"\r\n\t\"", var.example_api_calls)}"
EOT
}

resource "local_file" "test_script" {
  count = var.todos_as_local_files ? 1 : 0

  filename        = "test-${var.instance_id}.sh"
  file_permission = "755"
  content         = local.test_script
}


output "endpoint_url" {
  value = "${local.proxy_endpoint_url}/"
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

output "test_script" {
  value = try(local_file.test_script[0].filename, null)
}

output "test_script_content" {
  value = local.test_script
}

output "todo" {
  value = local.todo_content
}

output "next_todo_step" {
  value = var.todo_step + 1
}
