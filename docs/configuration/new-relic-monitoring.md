# New Relic Monitoring

> **Beta feature** — New Relic monitoring is supported in **AWS only**. Your mileage may vary (YMMV)
> and this feature may be removed at any time. Where there is ambiguity about New Relic configuration,
> defer to the [New Relic documentation](https://docs.newrelic.com/docs/serverless-function-monitoring/aws-lambda-monitoring/instrument-lambda-function/env-variables-lambda/).

## Setup

Add the following to your `terraform.tfvars` to enable New Relic monitoring:

```hcl
general_environment_variables = {
    NEW_RELIC_ACCOUNT_ID          = "{YOUR_NEW_RELIC_ACCOUNT_ID}"
    NEW_RELIC_LAMBDA_HANDLER      = "{YOUR_ORIGINAL_HANDLER}"
    NEW_RELIC_LICENSE_KEY         = "{YOUR_NEW_RELIC_LICENSE_KEY}"
    NEW_RELIC_APM_LAMBDA_MODE     = "true"
    NEW_RELIC_TRUSTED_ACCOUNT_KEY = "{YOUR_NEW_RELIC_ACCOUNT_ID_OR_PARENT_ID}"
}
```

(If you already have a defined `general_environment_variables` variable, just add the `NEW_RELIC_`
variables to it.)

## Required Variables

All five of the following environment variables must be set for New Relic monitoring to be enabled.
If only a subset is present, psoxy will log a warning and fall back to the default CloudWatch logging.

| Variable | Description |
|---|---|
| `NEW_RELIC_ACCOUNT_ID` | Your New Relic account ID |
| `NEW_RELIC_LAMBDA_HANDLER` | Your function's original handler (New Relic wraps it); e.g. `co.worklytics.psoxy.Handler` |
| `NEW_RELIC_LICENSE_KEY` | Your New Relic ingest license key. Overrides Secrets Manager if set. |
| `NEW_RELIC_APM_LAMBDA_MODE` | Set to `true` to enable APM monitoring |
| `NEW_RELIC_TRUSTED_ACCOUNT_KEY` | Your New Relic account ID or parent account ID (required for distributed tracing across account boundaries; often the same value as `NEW_RELIC_ACCOUNT_ID`) |

For further details on each variable and additional optional configuration, see the
[New Relic Lambda environment variables documentation](https://docs.newrelic.com/docs/serverless-function-monitoring/aws-lambda-monitoring/instrument-lambda-function/env-variables-lambda/).
