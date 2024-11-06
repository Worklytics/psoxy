# Using API Gateway (V2)

Some organizations require use of API Gateway. This is not the default approach for Psoxy since AWS
added support for Lambda Function URLs (March 2022), which are a simpler and more direct way to
expose lambdas via HTTPS.

Nonetheless, should you wish to use API Gateway we provide **beta** support for this. It is needed
if you wish to put your Lambda functions on a VPC (See `lambdas-on-vpc.md`).

In particular:

- IAM policy that allows api gateway methods to be invoked by the proxy caller role is defined once,
  using wildcards, and exposes GET/HEAD/POST methods for all resources. While methods are further
  constrained by routes and the proxy rules themselves, this could be another enforcement point at
  the infrastructure level - at expense of N policies + attachments in your terraform plan instead
  of 1.
- proxy instances exposed as lambda function urls have 55s timeout, but API Gateway seems to support
  30s as max - so this may cause timeouts in certain APIs

## Usage

Prerequisites:

- the AWS principal (user or role) to provision API gateways. The AWS managed policy
  [`AmazonAPIGatewayAdministrator`](https://docs.aws.amazon.com/aws-managed-policy/latest/reference/AmazonAPIGatewayAdministrator.html)
  provides this.

Add the following to your `terraform.tfvars` file:

```hcl
use_api_gateway_v2=true
```

Then `terraform apply` should create of API gateway-related resources, including policies/etc; and
destroy lambda function urls (if you've previously applied with `use_api_gateway=false`, which is
the default).

## API Gateway v1 - not supported, but FWIW

If you wish to use API Gateway V1, you will not be able to use the flag above. Instead, you'll have
to do something like the following:

```hcl

locals {
  rest_instances = {
    for id, instance in module.psoxy-aws-google-workspace.instances :
      id => instance if instance.proxy_kind == "rest"
  }
}

resource "aws_apigateway_api" "psoxy-api" {
  name          = "psoxy-api"
  protocol_type = "HTTP"
  description   = "API to expose psoxy instances"
}

resource "aws_cloudwatch_log_group" "gateway-log" {
  name              = aws_apigateway_api.psoxy-api.name
  retention_in_days = 7
}

resource "aws_apigatewayv2_stage" "live" {
  api_id      = aws_apigateway_api.psoxy-api.id
  name        = "live" # q: what name??
  auto_deploy = true
  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.gateway-log.arn
    format          = "$context.identity.sourceIp $context.identity.caller $context.identity.user [$context.requestTime] \"$context.httpMethod $context.path $context.protocol\" $context.status $context.responseLength $context.requestId $context.extendedRequestId $context.error.messageString $context.integrationErrorMessage"
  }
}


resource "aws_apigateway_integration" "map" {
  for_each = local.rest_instances

  api_id                    = aws_apigateway_api.psoxy-api.id
  integration_type          = "AWS_PROXY"
  connection_type           = "INTERNET"

  integration_method        = "POST"
  integration_uri           = each.value.function_arn
  request_parameters        = {}
  request_templates         = {}
}


resource "aws_apigateway_route" "get_route" {
  for_each = local.rest_instances

  api_id             = aws_apigateway_api.psoxy-api.id
  route_key          = "GET /${each.key}/{proxy+}"
  authorization_type = "AWS_IAM"
  target             = "integrations/${aws_apigateway_integration.map[each.key].id}"
}

resource "aws_apigateway_route" "head_route" {
  for_each = local.rest_instances

  api_id             = aws_apigatewayv2_api.psoxy-api.id
  route_key          = "HEAD /${each.key}/{proxy+}"
  authorization_type = "AWS_IAM"
  target             = "integrations/${aws_apigateway_integration.map[each.key].id}"
}

# allow API gateway to invoke the lambda function
resource "aws_lambda_permission" "lambda_permission" {
  for_each = local.rest_instances

  statement_id  = "Allow${each.key}Invoke"
  action        = "lambda:InvokeFunction"
  function_name = each.value.function_name
  principal     = "apigateway.amazonaws.com"


  # The /*/*/ part allows invocation from any stage, method and resource path
  # within API Gateway REST API.
  source_arn = "${aws_apigateway_api.psoxy-api.execution_arn}/*/*/${each.value.function_name}/{proxy+}"
}

resource "aws_iam_policy" "invoke_api" {
  name_prefix = "PsoxyInvokeAPI"

  policy = jsonencode({
    "Version" : "2012-10-17",
    "Statement" : [
      {
        "Effect" : "Allow",
        "Action" : "execute-api:Invoke",
        "Resource" : "arn:aws:execute-api:*:${var.aws_account_id}:${aws_apigateway_api.psoxy-api.id}/*/GET/*",
      },
      {
        "Effect" : "Allow",
        "Action" : "execute-api:Invoke",
        "Resource" : "arn:aws:execute-api:*:${var.aws_account_id}:${aws_apigateway_api.psoxy-api.id}/*/HEAD/*",
      },
    ]
  })
}

resource "aws_iam_role_policy_attachment" "invoke_api_policy_to_role" {
  role       = "PsoxyCaller" # Name (not ARN) of the API caller role
  policy_arn = aws_iam_policy.invoke_api.arn
}

```

Additionally, you'll need to set a different handler class to be invoked instead of the default
(`co.workltyics.psoxy.Handler`, should be `co.worklytics.psoxy.APIGatewayV1Handler`). This can be
done in Terraform or by modifying configuration via AWS Console.
