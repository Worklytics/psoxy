# Protips

Some ideas on how to support scenarios and configuration requirements beyond what our default
examples show:


## Encryption Keys
see [encryption-keys.md](encryption-keys.md)

## Tagging ALL infra created by your Terraform Configuration

If you're using our AWS example, it should support a `default_tags` variable.

You can add the following in your `terrform.tfvars` file to set tags on all resources created by the example configuration:

```hcl
default_tags = {
  Vendor = "Worklytics"
}
```

If you're not using our AWS example, you can add the following to your configuration, then you will need to modify
the `aws` provider block in your configuration to add a `default_tags`. Example shown below:

See: [https://registry.terraform.io/providers/hashicorp/aws/latest/docs#default_tags]

```hcl
provider "aws" {
  region = var.aws_region

  assume_role {
    role_arn = var.aws_assume_role_arn
  }

  default_tags {
    Vendor  = "Worklytics"
  }

  allowed_account_ids = [
    var.aws_account_id
  ]
}
```

## Using AWS API Gateway **alpha**

Worklytics' examples expose your Psoxy instances using AWS Lambda function URLs, which AWS released
in March 2022. These support secure invocation auth'd by AWS IAM. As Psoxy instances simply proxy
a restricted, sanitized view of REST APIs that are generally open on the public internet, we don't
consider there to be a security need to add further authentication layers that API Gateways could
be used to provide. (Especially given the complexity that such additional layers introduce; any
added complexity in a system increases potential for breach due to misconfiguration). Nonetheless,
should you wish to add an API Gateway in front of your Psoxy instances, it is possible and *should*
be compatible with the code.

### Using API Gateway V2
API Gateway V2 sends requests to lambdas with the same format as Function URLs; so slapping something
like the following onto one of our AWS examples should suffice:

```hcl
locals {
  rest_instances = {
    for id, instance in module.psoxy-aws-google-workspace.instances :
      id => instance if instance.proxy_kind == "rest"
  }
}

resource "aws_apigatewayv2_api" "psoxy-api" {
  name          = "psoxy-api"
  protocol_type = "HTTP"
  description   = "API to expose psoxy instances"
}

resource "aws_cloudwatch_log_group" "gateway-log" {
  name              = aws_apigatewayv2_api.psoxy-api.name
  retention_in_days = 7
}

resource "aws_apigatewayv2_stage" "live" {
  api_id      = aws_apigatewayv2_api.psoxy-api.id
  name        = "live" # q: what name??
  auto_deploy = true
  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.gateway-log.arn
    format          = "$context.identity.sourceIp $context.identity.caller $context.identity.user [$context.requestTime] \"$context.httpMethod $context.path $context.protocol\" $context.status $context.responseLength $context.requestId $context.extendedRequestId $context.error.messageString $context.integrationErrorMessage"
  }
}


resource "aws_apigatewayv2_integration" "map" {
  for_each = local.rest_instances

  api_id                    = aws_apigatewayv2_api.psoxy-api.id
  integration_type          = "AWS_PROXY"
  connection_type           = "INTERNET"

  integration_method        = "POST"
  integration_uri           = each.value.function_arn
  request_parameters        = {}
  request_templates         = {}
  payload_format_version    = "2.0"
}


resource "aws_apigatewayv2_route" "get_route" {
  for_each = local.rest_instances

  api_id             = aws_apigatewayv2_api.psoxy-api.id
  route_key          = "GET /${each.key}/{proxy+}"
  authorization_type = "AWS_IAM"
  target             = "integrations/${aws_apigatewayv2_integration.map[each.key].id}"
}

resource "aws_apigatewayv2_route" "head_route" {
  for_each = local.rest_instances

  api_id             = aws_apigatewayv2_api.psoxy-api.id
  route_key          = "HEAD /${each.key}/{proxy+}"
  authorization_type = "AWS_IAM"
  target             = "integrations/${aws_apigatewayv2_integration.map[each.key].id}"
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
  source_arn = "${aws_apigatewayv2_api.psoxy-api.execution_arn}/*/*/${each.value.function_name}/{proxy+}"
}

resource "aws_iam_policy" "invoke_api" {
  name_prefix = "PsoxyInvokeAPI"

  policy = jsonencode({
    "Version" : "2012-10-17",
    "Statement" : [
      {
        "Effect" : "Allow",
        "Action" : "execute-api:Invoke",
        "Resource" : "arn:aws:execute-api:*:${var.aws_account_id}:${aws_apigatewayv2_api.psoxy-api.id}/*/GET/*",
      },
      {
        "Effect" : "Allow",
        "Action" : "execute-api:Invoke",
        "Resource" : "arn:aws:execute-api:*:${var.aws_account_id}:${aws_apigatewayv2_api.psoxy-api.id}/*/HEAD/*",
      },
    ]
  })
}

resource "aws_iam_role_policy_attachment" "invoke_api_policy_to_role" {
  role       = "PsoxyCaller" # Name (not ARN) of the API caller role
  policy_arn = aws_iam_policy.invoke_api.arn
}

```

### Using API Gateway V1 **alpha**

You'll need to do something similar to above, but use plain `apigateway` Terraform resources instead
of V2. It will be a 'REST' api, instead of an 'HTTP' api.

Additionally, you'll need to set a different handler class to be invoked instead of the default
(`co.workltyics.psoxy.Handler`, should be `co.worklytics.psoxy.APIGatewayV1Handler`). This can be
done in Terraform or by modifying configuration via AWS Console..

## Extensibility

To support extensibility, our Terraform examples/modules output the IDs/names of the major resources they create, so
that you can compose them with other Terraform resources.

### Buckets

The `aws-host` module outputs `bulk_connector_instances`; a map of `id => instance` for each bulk connector. Each of
these has two attributes that correspond to the names of its related buckets:
  - `sanitized_bucket_name`
  - `input_bucket_name`

So in our AWS example, you can use these to enable logging, for example, you could do something like this: (YMMV, syntax
etc should be tested)

```hcl
local {
  id_of_bucket_to_store_logs = "{YOUR_BUCKET_ID_HERE}"
}

resource "aws_s3_bucket_logging" "logging" {
  for_each = module.psoxy.bulk_connector_instances

  bucket = each.value.sanitized_bucket_name

  target_bucket = local.id_of_bucket_to_store_logs
  target_prefix = "psoxy/${each.key}/"
}

resource "aws_s3_bucket_logging" "logging" {
  for_each = module.psoxy.bulk_connector_instances

  bucket = each.value.input_bucket_name

  target_bucket = local.id_of_bucket_to_store_logs
  target_prefix = "psoxy/${each.key}/"
}
```

Analogous approaches can be used to configure versioning, replication, etc;
  - [`aws_s3_bucket_versioning`](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket_versioning)
  - [`aws_s3_bucket_replication`](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket_replication)

Note that encryption, lifecycle, public_access_block are set by the Workltyics-provided modules, so you may have
conflicts issues if you also try to set those outside.







