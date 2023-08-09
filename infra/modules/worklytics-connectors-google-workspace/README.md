# worklytics-connectors

Connector specs + authentication / authorization for all Worklytics connectors that depend on the
Google terraform provider, as this depends on having `gcloud` installed and authenticated in the
environment running `terraform` commands.

NOTE: this module references the `google` provider, which depends on having GCloud CLI installed and
authenticated. If the environment where you're running Terraform does not have an authenticated
`gcloud` CLI, you will get errors even if no resources from this provider is are actually used by
your Terraform configuration.

If you are not using Google Workspace sources, DELETE or comment out all invocations of this
module from your Terraform configuration to avoid these errors.
