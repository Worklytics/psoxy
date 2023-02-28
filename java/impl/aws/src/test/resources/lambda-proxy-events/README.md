Example payloads delivered to AWS Lambda for HTTP requests to an API Gateway v2, via an integration
configured as an 'AWS_PROXY'.


Examples:
 - `api-gateway-v2-event.json` - taken from https://github.com/awsdocs/aws-lambda-developer-guide/blob/main/sample-apps/nodejs-apig/event-v2.json
 - `api-gateway-v2-event_ours.json` - guess of how API Gateway v2 payload looks like given expected stages+routes in real deployment
 - `generic-request.json` - looks like a direct Lambda URL invocation
 - `generic-request_explicit-stage.json` - looks like a API Gateway v1 payload??
 - `api-gateway-v1-example.json` - uninteresting/incomplete example that appears to be v1 from https://docs.aws.amazon.com/lambda/latest/dg/services-apigateway.html#apigateway-proxy


