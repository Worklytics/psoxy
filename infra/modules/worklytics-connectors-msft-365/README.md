# worklytics-connectors-msft-365

Connector specs + authentication / authorization for all Worklytics connectors that depend on the
Azure AD terraform provider (eg Microsoft 365), as this depends on having `az` installed and
authenticated in the environment running `terraform` commands.

NOTE: this module references the `azuread` provider, which depends on having `az` CLI installed and
authenticated. If the environment where you're running Terraform does not have an authenticated
`az` CLI, you will get errors even if no resources from this provider is are actually used by
your Terraform configuration.

If you are not using Microsoft 365 sources, DELETE or comment out all invocations of this
module from your Terraform configuration to avoid these errors.
