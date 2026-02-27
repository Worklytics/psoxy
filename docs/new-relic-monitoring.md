# New Relic Monitoring

For customers wishing to using New Relic to monitor their proxy instances, we have **alpha**
support for this in AWS. We provide no guarantee as to how it works, nor as to whether its behavior
will be maintained in the future.

To enable,

  1. Set your proxy release to `new-relic_v0.4.62`.

  2. Add the following to your `terraform.tfvars` to configure it:

```tf
general_environment_variables = {
    NEW_RELIC_ACCOUNT_ID             = "{YOUR_NEW_RELIC_ACCOUNT_ID}"
    NEW_RELIC_PRIMARY_APPLICATION_ID = "{YOUR_NEW_RELIC_ACCOUNT_ID}"
    NEW_RELIC_TRUSTED_ACCOUNT_KEY     = "{YOUR_NEW_RELIC_ACCOUNT_ID}"
}
```
  (if you already have a defined `general_environment_variables` variable, just add the `NEW_RELIC_`
  variables to it)

Additionally, you must set the following for each of these cases:
  - API Connectors:
      - `NEW_RELIC_LAMBDA_HANDLER = "co.worklytics.psoxy.Handler"`
  - Bulk Data Connectors:
      - `NEW_RELIC_LAMBDA_HANDLER = "co.worklytics.psoxy.S3Handler"`


See also:

https://docs.newrelic.com/docs/serverless-function-monitoring/aws-lambda-monitoring/instrument-lambda-function/env-variables-lambda/#java


