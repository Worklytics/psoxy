# worklytics-connectors

Connector specs + authentication / authorization for all Worklytics connectors that depend on the
Google terraform provider, as this depends on having `gcloud` installed and authenticated in the
environment running `terraform` commands.

## Background
The `azuread` provider depends on having Azure CLI installed and authenticated; and the
`google` provider depends on having GCloud CLI  installed and authenticated. This is required even
if no resources from these providers are actually used by your Terraform configuration. While it
wouldn't be terrible to have people install these, it's not reasonable to expect them to have
Google / Microsoft 365 user accounts for the purpose of authenticating them.

See [Spec : Terraform Module Design](https://docs.google.com/document/d/1iZG7R3gXRt0riDk8H6Ryre0VzByLyX_RVlYyVqNvYDY/edit) for details.

