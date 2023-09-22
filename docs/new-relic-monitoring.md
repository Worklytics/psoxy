# New Relic Monitoring



Only supported for AWS.

To enable new relic monitoring, add the following to your `terraform.tfvars` to configure it:

```tf
general_environment_variables = {
    NEW_RELIC_ACCOUNT_ID             = "{YOUR_NEW_RELIC_ACCOUNT_ID}"
    NEW_RELIC_PRIMARY_APPLICATION_ID = "{YOUR_NEW_RELIC_ACCOUNT_ID}"
    NEW_RELIC_TRUSTED_ACCOUNT_ID     = "{YOUR_NEW_RELIC_ACCOUNT_ID}"
}
```
(if you already have a defined `general_environment_variables` variable, just add the `NEW_RELIC_`
variables to it)

As of September 2023, this is an *alpha* feature; it is only available by setting your proxy release
version to ` v0.4.39-alpha.new-relic.1`.

