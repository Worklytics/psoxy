# provision a Psoxy instance into AWS account

terraform {
  required_providers {
    aws = {
      version = "~> 3.0"
    }
  }
}


# map API gateway request --> lambda function backend
resource "aws_apigatewayv2_integration" "map" {
  api_id                    = var.api_gateway.id
  integration_type          = "AWS_PROXY"
  connection_type           = "INTERNET"

  integration_method        = "POST"
  integration_uri           = aws_lambda_function.psoxy-instance.invoke_arn
  request_parameters        = {}
  request_templates         = {}
}


resource "aws_apigatewayv2_route" "example" {
  api_id             = var.api_gateway.id
  route_key          = "GET /${var.function_name}/{proxy+}"
  authorization_type = "AWS_IAM"
  target             = "integrations/${aws_apigatewayv2_integration.map.id}"
}

# allow API gateway to invoke the lambda function
resource "aws_lambda_permission" "lambda_permission" {
  statement_id  = "Allow${var.function_name}Invoke"
  action        = "lambda:InvokeFunction"
  function_name = var.function_name
  principal     = "apigateway.amazonaws.com"

  # The /*/*/* part allows invocation from any stage, method and resource path
  # within API Gateway REST API.
  source_arn = "${var.api_gateway.arn}/*/*/*"

  depends_on = [
    aws_lambda_function.psoxy-instance
  ]
}

locals {
  proxy_endpoint_url = "${var.api_gateway.api_endpoint}/${var.function_name}"
}

resource "aws_iam_role" "iam_for_lambda" {
  name = "iam_for_lambda_${var.function_name}"

  assume_role_policy = jsonencode({
    "Version": "2012-10-17",
    "Statement": [
      {
        "Action": "sts:AssumeRole",
        "Principal": {
          "Service": "lambda.amazonaws.com"
        },
        "Effect": "Allow",
        "Sid": ""
      }
    ]
  })
  /* needed?
  {
        "Action": "sts:AssumeRole",
        "Principal": {
          "Service": "apigateway.amazonaws.com"
        },
        "Effect": "Allow",
        "Sid": ""
      }
      */
}

resource "aws_lambda_function" "psoxy-instance" {
  function_name    = var.function_name
  role             = aws_iam_role.iam_for_lambda.arn
  handler          = "co.worklytics.psoxy.Handler"
  runtime          = "java11"
  filename         = var.path_to_function_zip
  source_code_hash = filebase64sha256(var.path_to_function_zip)
  timeout          = 55 # seconds
  memory_size      = 512 # megabytes

  environment {
    # NOTE: can use merge() to combine var map from config with additional values
    variables = yamldecode(file(var.path_to_config))
  }
}


// User: arn:aws:sts::874171213677:assumed-role/iam_for_lambda/psoxy-gdirectory is not authorized to perform:  on resource: arn:aws:ssm:us-east-1:874171213677:parameter/RULES because no identity-based policy allows the ssm:GetParameter action

resource "aws_iam_policy" "policy" {
  name        = "${var.function_name}_ssmGetParameters"
  description = "Allow lambda function role to read SSM parameters"

  policy = jsonencode(
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "ssm:GetParameter*"
      ],
      "Effect": "Allow",
      "Resource": "*"
      # TODO: limit to SSM parameters in question
      # "Resource": "arn:aws:ssm:us-east-2:123456789012:parameter/prod-*"
    }
  ]
})

}



resource "aws_iam_role_policy_attachment" "basic" {
  role       = aws_iam_role.iam_for_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy_attachment" "policy"{
  role       = aws_iam_role.iam_for_lambda.name
  policy_arn = aws_iam_policy.policy.arn
}


resource "local_file" "todo" {
  filename = "TODO - deploy ${var.function_name}.md"
  content  = <<EOT
First, from `java/core/` within a checkout of the Psoxy repo, package the core proxy library:

```shell
cd ../../java/core
mvn package install
```

Second, from `java/impl/aws` within a checkout of the Psoxy repo, package an executable JAR for the
cloud function with the following command:

```shell
cd ../../java/impl/aws
mvn package
```

Third, run the following deployment command from `java/impl/aws` folder within your checkout:

```shell
aws sts assume-role --duration 900 --role-arn ${var.aws_assume_role_arn} --role-session-name deploy-lambda > temporal-credentials.json
export AWS_ACCESS_KEY_ID=`cat temporal-credentials.json| jq -r '.Credentials.AccessKeyId'`
export AWS_SECRET_ACCESS_KEY=`cat temporal-credentials.json| jq -r '.Credentials.SecretAccessKey'`
export AWS_SESSION_TOKEN=`cat temporal-credentials.json| jq -r '.Credentials.SessionToken'`
rm temporal-credentials.json
aws sts get-caller-identity

aws lambda update-function-code --function-name ${var.function_name} --zip-file fileb://target/psoxy-aws-1.0-SNAPSHOT.jar

unset AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN
aws sts get-caller-identity

```

Finally, review the deployed function in AWS console:

TBD

## Testing

Requests to AWS API need to be [signed](https://docs.aws.amazon.com/general/latest/gr/signing_aws_api_requests.html).
One tool to do it easily is [awscurl](https://github.com/okigan/awscurl). Install it:

```shell
pip install awscurl
```

```shell
export PSOXY_HOST=${var.api_gateway.api_endpoint}/live/${var.function_name}
aws sts assume-role --role-arn ${var.api_caller_role_arn} --role-session-name ${var.function_name}_local_test --output json > token.json
export AWS_ACCESS_KEY_ID=`cat token.json| jq -r '.Credentials.AccessKeyId'`
export AWS_SECRET_ACCESS_KEY=`cat token.json| jq -r '.Credentials.SecretAccessKey'`
export AWS_SESSION_TOKEN=`cat token.json| jq -r '.Credentials.SessionToken'`
# TODO: set meaningful paths per integration
export PSOXY_PATH=/admin/directory/v1/customer/my_customer/domains

awscurl -v -i --service execute-api --access_key $AWS_ACCESS_KEY_ID --secret_key $AWS_SECRET_ACCESS_KEY --security_token $AWS_SESSION_TOKEN $PSOXY_HOST$PSOXY_PATH

rm token.json
unset AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN
```

NOTE: if you want to customize the rule set used by Psoxy for your source, you can add a
`rules.yaml` file into the deployment directory (`target/deployment`) before invoking the command
above. The rules you define in the YAML file will override the ruleset specified in the codebase for
the source.

EOT
}

output "endpoint_url" {
  value = local.proxy_endpoint_url
}
