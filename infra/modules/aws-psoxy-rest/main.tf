# provision a Psoxy instance into AWS account

terraform {
  required_providers {
    aws = {
      version = "~> 4.12"
    }
  }
}

module "psoxy_lambda" {
  source = "../aws-psoxy-lambda"

  function_name                  = var.function_name
  handler_class                  = "co.worklytics.psoxy.Handler"
  path_to_function_zip           = var.path_to_function_zip
  function_zip_hash              = var.function_zip_hash
  memory_size_mb                 = 512
  timeout_seconds                = 55
  reserved_concurrent_executions = var.reserved_concurrent_executions
  aws_assume_role_arn            = var.aws_assume_role_arn
  path_to_config                 = var.path_to_config
  source_kind                    = var.source_kind
  function_parameters            = var.function_parameters
  global_parameters              = var.global_parameters
  environment_variables          = var.environment_variables
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
  test_commands = [for path in var.example_api_calls :
    "node ${var.path_to_repo_root}tools/psoxy-test/cli.js -r \"${var.aws_assume_role_arn}\" -u \"${local.proxy_endpoint_url}${path}\"${local.impersonation_param}"
  ]
}

resource "local_file" "todo" {
  filename = "test ${var.function_name}.md"
  content  = <<EOT

## Testing

Review the deployed function in AWS console:

- https://console.aws.amazon.com/lambda/home?region=${var.region}#/functions/${var.function_name}?tab=monitoring

### Make "test calls" using our Psoxy testing tool

Based on your configuration, these are some example test calls you can try using our Node.js-based Psoxy testing tool (YMMV):

```shell
${coalesce(join("\n", local.test_commands), "cd docs/example-api-calls/")}
```

To be able to run the commands above you need Node.js (>=16) and npm (v >=8) installed. Please, check
the documentation of our Psoxy testing tool (`/tools/psoxy-test/README.md`) for a detailed description
of all the different options.

See `docs/example-api-calls/` for more example API calls specific to the data source to which your
Proxy is configured to connect.

Feel free to try the above calls, and reference to the source's API docs for other parameters /
endpoints to experiment with. If you spot any additional fields you believe should be
redacted/pseudonymized, feel free to modify the rules in your local repo and re-deploy OR configure
a RULES variable in the source.

Contact support@worklytics.co for assistance modifying the rules as needed.

EOT
}

output "endpoint_url" {
  value = aws_lambda_function_url.lambda_url.function_url
}

output "function_arn" {
  value = module.psoxy_lambda.function_arn
}
