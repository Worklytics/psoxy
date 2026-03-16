# New Relic Monitoring

> **Beta feature** — New Relic monitoring is supported in **AWS only**. Your mileage may vary (YMMV)
> and this feature may be removed at any time. Where there is ambiguity about New Relic configuration,
> defer to the [New Relic documentation](https://docs.newrelic.com/docs/serverless-function-monitoring/aws-lambda-monitoring/instrument-lambda-function/env-variables-lambda/).

## Setup

Add your `new_relic_account_id` to your `terraform.tfvars` to enable New Relic monitoring:

```hcl
new_relic_account_id = "{YOUR_NEW_RELIC_ACCOUNT_ID}"
```

When you provide this value, Psoxy's Terraform will automatically configure the `NEW_RELIC_ACCOUNT_ID` and the correct `NEW_RELIC_LAMBDA_HANDLER` environment variables for each Lambda function based on its deployment mode.

### Additional Configuration

You can provide additional environment variables to customize your New Relic configuration, such as your license key or APM tracking. Add these to your `general_environment_variables` in `terraform.tfvars`:

```hcl
general_environment_variables = {
    NEW_RELIC_LICENSE_KEY         = "{YOUR_NEW_RELIC_LICENSE_KEY}"
    NEW_RELIC_APM_LAMBDA_MODE     = "true"
    NEW_RELIC_TRUSTED_ACCOUNT_KEY = "{YOUR_NEW_RELIC_ACCOUNT_ID_OR_PARENT_ID}"
}
```

(If you already have a defined `general_environment_variables` variable, just add the `NEW_RELIC_` variables to it.)

### Reference: Lambda Handlers

Psoxy automatically configures the `NEW_RELIC_LAMBDA_HANDLER` depending on the proxy mode. As a reference, the handler mappings are:

| Proxy Mode / Configuration | AWS Lambda Handler (`NEW_RELIC_LAMBDA_HANDLER`) |
|---|---|
| **REST** (Default, API Gateway v2) | `co.worklytics.psoxy.Handler` |
| **REST** (API Gateway v1) | `co.worklytics.psoxy.APIGatewayV1Handler` |
| **REST** (Async Processing Enabled) | `co.worklytics.psoxy.AwsApiDataModeHybridHandler` |
| **Bulk** (S3 Input) | `co.worklytics.psoxy.S3Handler` |
| **Webhook Collector** | `co.worklytics.psoxy.AwsWebhookCollectionModeHandler` |

For further details on each variable and additional optional configuration, see the
[New Relic Lambda environment variables documentation](https://docs.newrelic.com/docs/serverless-function-monitoring/aws-lambda-monitoring/instrument-lambda-function/env-variables-lambda/).
