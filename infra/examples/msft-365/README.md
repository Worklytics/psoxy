# msft-365

This example provisions Azure AD app registration in Azure AD, without directly depending on anything
in host env (AWS/GCP) OR provisioning anything into that host env.

Use case is organizations whose AWS admins lack Azure AD perms, and vice-versa - such that each
component will be run separately.

As of Aug 2022, this is alpha-quality and generally not tested/recommended. Please carefully
review Terraform plan and be prepared to debug issues.

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

As of `v0.4.11`, authentication of your instance with Microsoft 365 is done via identity federation,
establishing a trust relationship between your Azure AD Application and your AWS account,
allowing the latter to authenticate as the former to access Microsoft 365 APIs. This approach
requires no shared secrets between your AWS account and your Microsoft 365 tenant, nor any secrets/
certficates to be generated/stored on your machine or in your AWS account. As such, this alone will
not result in anything sensitive being in your Terraform state files.
