# New Relic Monitoring

For customers wishing to use New Relic to monitor their proxy instances, we provide **beta** support for this in **AWS only**. Please note: your mileage may vary (YMMV) and this feature may be removed at any time.

To enable, add the following to your `terraform.tfvars` to configure it:

```tf
general_environment_variables = {
    NEW_RELIC_ACCOUNT_ID             = "{YOUR_NEW_RELIC_ACCOUNT_ID}"
    NEW_RELIC_PRIMARY_APPLICATION_ID = "{YOUR_NEW_RELIC_APPLICATION_ID}"
    NEW_RELIC_TRUSTED_ACCOUNT_KEY    = "{YOUR_NEW_RELIC_TRUSTED_ACCOUNT_KEY}"
}
```

(If you already have a defined `general_environment_variables` variable, just add the `NEW_RELIC_`
variables to it)
