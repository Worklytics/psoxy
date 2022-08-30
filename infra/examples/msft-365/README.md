# msft-365

This example provisions Azure AD app registration in Azure AD, without directly depending on anything
in host env (AWS/GCP) OR provisioning anything into that host env.

Use case is organizations whose AWS admins lack Azure AD perms, and vice-versa - such that each
component will be run separately.

As of Aug 2022, this is alpha-quality and generally not tested/recommended. Please carefully
review Terraform plan and be prepared to debug issues.

NOTE: this generates local certificate/etc. You can always remove these from Azure AD, and replace
with your own generated out of band. Goal of terraform scripts is to simplify everything.

## Authentication


### Azure AD (Microsoft 365)
Download and install the [Azure CLI](https://docs.microsoft.com/en-us/cli/azure/install-azure-cli).
This example presumes you'll authenticate that via `az login`, such that the [Terraform Azure
provider](https://registry.terraform.io/providers/hashicorp/azuread/latest/docs) will leverage those
credentials for authentication. This is Terraform's recommended approach for authenticating when
running locally/interactively and our documentation will generally presume you're using this
approach.  If that doesn't suite your organization needs, the Terraform docs linked above provide
alternatives, but this will require your own modification to the example configuration.

If your target MSFT tenant (specified in `terraform.tfvars`) lacks an Azure subscription (eg, is
only Microsoft 365), you should auth with `az login --allow-no-subscriptions`.

## Example Configuration

Example `terraform.tfvars`:
```terraform
msft_tenant_id                = "some-uuid-of-msft-tenant" # should hold your Microsoft 365 instance
```

## Security

This example includes generation of certificates for your Azure AD application listings.
   - anyone in possession of those certificates could access your data with whatever permissions you
     grant to the Azure AD application.
   - this example should be used with caution and only run from a location that, based on your
     org's infosec rules, can be used to generate such a certificate.
   - the terraform state produced by this module should be persisted to a secure location, such as
     an encrypted disk of a VM dedicated for this purpose OR a cloud storage service (GCS, S3 etc).
     It is generally NOT good practice to commit such state files to a source code repository such
     as git/github.


