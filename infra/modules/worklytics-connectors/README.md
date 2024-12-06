# worklytics-connectors

Connector specs + authentication / authorization for all Worklytics connectors that don't depend on
Terraform providers with additional dependencies.


## Background
Namely, the `azuread` provider depends on having Azure CLI installed and authenticated; and the
`google` provider depends on having GCloud CLI  installed and authenticated. This is required even
if no resources from these providers are actually used by your Terraform configuration. While it
wouldn't be terrible to have people install these, it's not reasonable to expect them to have
Google / Microsoft 365 user accounts for the purpose of authenticating them.

See [Spec : Terraform Module Design](https://docs.google.com/document/d/1iZG7R3gXRt0riDk8H6Ryre0VzByLyX_RVlYyVqNvYDY/edit) for details.

## Outputs

`enabled_connectors_rest`
```hcl
type = map(object({
    source_kind = string
    worklytics_connector_id = string
    worklytics_connector_name = string
    source_auth_strategy = string
    target_host = string
    environment_variables = map(string)
    example_api_calls = list(string)
    example_api_calls_user_to_impersonate = string
    secured_variables = list(object({
        name = string
        value = string
        writable = boolean
    }))
  })
)
```


`enabled_connectors_bulk`
```hcl
type = map(object({
    source_kind = string
    worklytics_connector_id = string
    worklytics_connector_name = string
    rules = object({
        columns_to_redact = list(string)
        columns_to_pseudonymize = list(string)
    })
    example_file = string
  })
)
```
