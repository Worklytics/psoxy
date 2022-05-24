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

  function_name        = var.function_name
  handler_class        = "co.worklytics.psoxy.Handler"
  path_to_function_zip = var.path_to_function_zip
  function_zip_hash    = var.function_zip_hash
  memory_size_mb       = 512
  timeout_seconds      = 55
  aws_assume_role_arn  = var.aws_assume_role_arn
  path_to_config       = var.path_to_config
  source_kind          = var.source_kind
  parameters           = []
}

resource "aws_lambda_function_url" "lambda_url" {
  function_name      = var.function_name
  authorization_type = "AWS_IAM"

  cors {
    allow_credentials = true
    allow_origins     = ["*"]
    allow_methods     = ["POST", "GET", "HEAD"] # TODO: configurable? not all require POST
    allow_headers     = ["date", "keep-alive"]
    expose_headers    = ["keep-alive", "date"]
    max_age           = 86400
  }
}

resource "aws_iam_role" "iam_for_lambda" {
  name = "iam_for_lambda_${var.function_name}"

  assume_role_policy = jsonencode({
    "Version" : "2012-10-17",
    "Statement" : [
      {
        "Action" : "sts:AssumeRole",
        "Principal" : {
          "Service" : "lambda.amazonaws.com"
        },
        "Effect" : "Allow",
        "Sid" : ""
      }
    ]
  })
}

resource "aws_iam_policy" "policy" {
  name        = "${var.function_name}_ssmGetParameters"
  description = "Allow lambda function role to read SSM parameters"

  policy = jsonencode(
    {
      "Version" : "2012-10-17",
      "Statement" : [
        {
          "Action" : [
            "ssm:GetParameter*"
          ],
          "Effect" : "Allow",
          "Resource" : "arn:aws:ssm:${var.region}:${var.aws_account_id}:parameter/*"
        }
      ]
  })
}


resource "aws_iam_role_policy_attachment" "basic" {
  role       = aws_iam_role.iam_for_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy_attachment" "policy" {
  role       = aws_iam_role.iam_for_lambda.name
  policy_arn = aws_iam_policy.policy.arn
}

locals {
  # lamba_url has trailing /, but our example_api_calls already have preceding /
  proxy_endpoint_url = substr(aws_lambda_function_url.lambda_url.function_url, 0, -1)
  test_commands = [for path in var.example_api_calls :
    "${var.path_to_repo_root}/tools/test-psoxy.sh -a -r \"${var.aws_assume_role_arn}\" -u \"${local.proxy_endpoint_url}${path}\""
  ]
}

resource "local_file" "todo" {
  filename = "test ${var.function_name}.md"
  content  = <<EOT

## Testing

Review the deployed function in AWS console:

- https://console.aws.amazon.com/lambda/home?region=${var.region}#/functions/${var.function_name}?tab=monitoring

### Prereqs
Requests to AWS API need to be [signed](https://docs.aws.amazon.com/general/latest/gr/signing_aws_api_requests.html).
One tool to do it easily is [awscurl](https://github.com/okigan/awscurl). Install it:

On MacOS via Homebrew:
```shell
brew install awscurl
```

Alternatively, via `pip` (python package manager):
```shell
pip install awscurl
# Installs in $HOME/.local/bin
# Make it available in your path
# Add the following line to ~/.bashrc
export PATH="$HOME/.local/bin:$PATH"
# Then reload the config
source ~/.bashrc
```

### From Terminal

From root of your checkout of the Psoxy repo, these are some example test calls you can try (YMMV):

```shell
${coalesce(join("\n", local.test_commands), "cd docs/example-api-calls/")}
```

See `docs/example-api-calls/` for more example API calls specific to the data source to which your
Proxy is configured to connect.

EOT
}

output "endpoint_url" {
  value = aws_lambda_function_url.lambda_url.function_url
}

output "function_arn" {
  value = module.psoxy_lambda.function_arn
}

output "iam_for_lambda_arn" {
  value = aws_iam_role.iam_for_lambda.arn
}

output "iam_for_lambda_name" {
  value = aws_iam_role.iam_for_lambda.name
}
