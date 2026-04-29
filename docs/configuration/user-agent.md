# User-Agent Configuration

> **Status**: BETA

Psoxy sends a `User-Agent` header on all outbound HTTP requests to data sources. By default, this
identifies the proxy version and Java runtime:

```
Worklytics Psoxy/v0.6.0 (Java 21.0.1)
```

## Customizing the User-Agent

You can override the `User-Agent` string by setting the `USER_AGENT` environment variable. This
fully replaces the default value.

Use the `general_environment_variables` Terraform variable to configure this globally for all
connectors:

### AWS

```hcl
# in your terraform.tfvars or equivalent
general_environment_variables = {
  USER_AGENT = "MyCompany Psoxy/1.0"
}
```

### GCP

```hcl
# in your terraform.tfvars or equivalent
general_environment_variables = {
  USER_AGENT = "MyCompany Psoxy/1.0"
}
```

The `general_environment_variables` map is applied to **all** connector instances (API, bulk, and
webhook collectors). Any key you set here takes precedence over module-computed defaults and
per-connector environment variables.

## Verifying the Configuration

After deploying, you can verify the active `User-Agent` string by calling the proxy's health check
endpoint. The response JSON includes a `userAgent` field:

```json
{
  "configuredHost": "graph.microsoft.com",
  "userAgent": "MyCompany Psoxy/1.0",
  "version": "v0.6.0",
  ...
}
```

## Notes

- If `USER_AGENT` is not set, the proxy uses a default string built from the product name, version,
  and Java runtime version.
- The `USER_AGENT` value is applied globally via the `HttpRequestFactory`, so it applies to all
  outbound HTTP requests including OAuth token operations.
- Some data sources may use `User-Agent` for rate limiting, auditing, or access control. Consult
  your source's API documentation before changing this value.
