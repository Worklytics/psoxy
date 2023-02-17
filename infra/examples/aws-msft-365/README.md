# AWS + Microsoft 365

This example provisions psoxy as an AWS lambda that connects to Microsoft 365 data sources.

## Getting Started

### AWS
Follow [`docs/aws/getting-started.md`](../../../docs/aws/getting-started.md) to setup AWS CLI to
authenticate as a user/role which can access/create the AWS account in which you wish to provision
your psoxy instance.

### Azure AD (Microsoft 365)
Download and install the [Azure CLI](https://docs.microsoft.com/en-us/cli/azure/install-azure-cli).
This example presumes you'll authenticate that via `az login`, such that the [Terraform Azure
provider](https://registry.terraform.io/providers/hashicorp/azuread/latest/docs) will leverage those
credentials for authentication. This is Terraform's recommended approach for authenticating when
running locally/interactively and our documentation will generally presume you're using this
approach.  If that doesn't suite your organization needs, the Terraform docs linked above provide
alternatives, but this will require your own modification to the example configuration.

If your target MSFT tenant (specified in `terraform.tfvars`) lacks an Azure subscription (eg, is
only Microsoft 365), you should auth with `az login --allow-no-subscriptions`. (or run `./az-auth)

## Usage

We recommend you make a copy of this directory and customize it for your org.

Run `./init` in this directory to create an example `terraform.tfvars` file.  Edit it to customize.

Create a file in this directory named `terraform.tfvars` to specify your settings:

Initialize your configuration (at this location in directory hierarchy):
```shell
terraform init
```

Apply
```shell
terraform apply
```

Review the plan and confirm to apply.


## Security

As of `v0.4.11`, authentication of your instance with Microsoft 365 is done via identity federation,
establishing a trust relationship between your Azure AD Application and your AWS account,
allowing the latter to authenticate as the former to access Microsoft 365 APIs. This approach
requires no shared secrets between your AWS account and your Microsoft 365 tenant, nor any secrets/
certficates to be generated/stored on your machine or in your AWS account. As such, this alone will
not result in anything sensitive being in your Terraform state files.

