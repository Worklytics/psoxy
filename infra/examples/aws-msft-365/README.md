# aws-msft-365

This example provisions psoxy as an AWS lambda that connects to Microsoft 365 data sources.

## Authentication

### AWS
Follow [`docs/aws/getting-started.md`](../../../docs/aws/getting-started.md) to setup AWS CLI to
authenticate as a user/role which can access/create the AWS account in which you wish to provision
your psoxy instance.

### Azure AD (Microsoft 365)
Download and install the [Azure CLI](https://docs.microsoft.com/en-us/cli/azure/install-azure-cli).
This example presumses you'll authenticate that via `az login`, such that the [Terraform Azure
provider](https://registry.terraform.io/providers/hashicorp/azuread/latest/docs) will leverage those
credentials for authentication. This is Terraform's recommended approach for authenticating when
running locally/interactively and our documentation will generally presume you're using this
approach.  If that doesn't suite your organization needs, the Terraform docs linked above provide
alternatives, but this will require your own modification to the example configuration.

If your target MSFT tenant (specified in `terraform.tfvars`) lacks an Azure subscriptiong (eg, is
only Microsoft 365), you should auth with `az login --allow-no-subscriptions`.


## Example Configuration

Example `terraform.tfvars`:
```terraform
aws_account_id                = "123456789"
aws_assume_role_arn           = "arn:aws:iam::123456789:role/InfraAdmin"
environment_name              = "dev-aws"
connector_display_name_suffix = " Psoxy Dev AWS - erik"
msft_tenant_id                = "some-uuid-of-msft-tenant" # should hold your Microsoft 365 instance
caller_aws_account_id         = "914358739851:root"
caller_external_user_id       = "your-worklytics-service-account"
```

