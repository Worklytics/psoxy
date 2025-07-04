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
    }
    : k => v if v != null
  }

  arn_for_test_calls = var.api_caller_role_arn

  # helper to clarify conditionals throughout
  use_api_gateway = var.api_gateway_v2 != null

  # handler MUST expect payload format.
  # payload 2.0 format is used by function URL invocation AND APIGatewayV2 by default.
  # but in latter case, seems to urldecode the path; such that /foo%25/bar becomes /foo//bar, which is not what we want
  # so oddly, for APIGatewayV2 we need to use 1.0 format instead of its default , even though that default is our usual case otherwise
  event_handler_implementation = local.use_api_gateway ? "APIGatewayV1Handler" : "Handler"

  provision_side_output_original_bucket  = try(var.side_output_original != null && var.side_output_original.bucket == null, false)
  provision_side_output_sanitized_bucket = try(var.side_output_sanitized != null && var.side_output_sanitized.bucket == null, false)

}

# a unique sequence to commonly name this instance's buckets, but distinguish them globally
resource "random_string" "bucket_unique_sequence" {
  length  = 8
  lower   = true
  upper   = false
  special = false
  numeric = true
}

module "async_output" {
  count = var.enable_async_processing ? 1 : 0

  source = "../aws-output-s3"

  environment_name                     = var.environment_name
  instance_id                          = var.instance_id
  unique_sequence                      = random_string.bucket_unique_sequence.result
  bucket_suffix                        = "async-output"
  provision_bucket_public_access_block = true
  lifecycle_ttl_days                   = 90 # 3 months, more than enough
}

module "async_output_iam_statements" {
  count = var.enable_async_processing ? 1 : 0

  source = "../aws-bucket-read-write-iam-policy-statement"

  s3_path = "s3://${module.async_output[0].bucket_id}"
}

module "side_output_original" {
  count = local.provision_side_output_original_bucket ? 1 : 0

  source = "../aws-output-s3"

  environment_name                     = var.environment_name
  instance_id                          = var.instance_id
  unique_sequence                      = random_string.bucket_unique_sequence.result
  bucket_suffix                        = "side-output-original"
  provision_bucket_public_access_block = true
  lifecycle_ttl_days                   = 720 # 2 years
}

module "side_output_sanitized" {
  count = local.provision_side_output_sanitized_bucket ? 1 : 0

  source = "../aws-output-s3"

  environment_name                     = var.environment_name
  instance_id                          = var.instance_id
  unique_sequence                      = random_string.bucket_unique_sequence.result
  bucket_suffix                        = "side-output-sanitized"
  provision_bucket_public_access_block = true
  lifecycle_ttl_days                   = 720 # 2 years
}

module "psoxy_lambda" {
  source = "../aws-psoxy-lambda"

  environment_name                     = var.environment_name
  instance_id                          = var.instance_id
  handler_class                        = "co.worklytics.psoxy.${local.event_handler_implementation}"
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
  side_output_original                 = local.provision_side_output_original_bucket ? "s3://${module.side_output_original[0].bucket_id}" : try(var.side_output_original.bucket, null)
  side_output_sanitized                = local.provision_side_output_sanitized_bucket ? "s3://${module.side_output_sanitized[0].bucket_id}" : try(var.side_output_sanitized.bucket, null)
  lambda_role_iam_statements           = var.enable_async_processing ? module.async_output_iam_statements[0].iam_statements : []

  environment_variables = merge(
    var.environment_variables,
    local.required_env_vars,
    var.enable_async_processing ? { ASYNC_OUTPUT_DESTINATION = "s3://${module.async_output[0].bucket_id}" } : {},
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
  payload_format_version = "1.0" # must match to handler value, set in lambda
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
  test_script = templatefile("${path.module}/test_script.tftpl", {
    proxy_endpoint_url  = local.proxy_endpoint_url,
    function_name       = module.psoxy_lambda.function_name,
    impersonation_param = local.impersonation_param,
    command_cli_call    = local.command_cli_call,
    example_api_calls   = var.example_api_calls,
  })
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
  value       = "rest" # TODO: revisit; this makes assumption about nature of the API that is overly restrictive and unnecessary
  description = "The kind of proxy instance this is."
}

output "test_script" {
  value = try(local_file.test_script[0].filename, null)
}

output "test_script_content" {
  value = local.test_script
}

output "async_output_bucket_id" {
  value       = try(module.async_output[0].bucket_id, null)
  description = "Bucket ID of the async output bucket, if any. May have been provided by the user, or provisioned by this module."
}

output "side_output_original_bucket_id" {
  value       = try(module.side_output_original[0].bucket_id, var.side_output_original.bucket, null)
  description = "Bucket ID of the side output bucket for original data, if any. May have been provided by the user, or provisioned by this module."
}

output "side_output_sanitized_bucket_id" {
  value       = try(module.side_output_sanitized[0].bucket_id, var.side_output_sanitized.bucket, null)
  description = "Bucket ID of the side output bucket for sanitized data, if any. May have been provided by the user, or provisioned by this module."
}

output "todo" {
  value = local.todo_content
}

output "next_todo_step" {
  value = var.todo_step + 1
}
