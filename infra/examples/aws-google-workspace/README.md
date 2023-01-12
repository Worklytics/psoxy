# AWS + Google Workspace

Example Terraform configuration for deploying psoxy in AWS and connecting to Google Workspace sources.

See [../modules/aws-google-workspace] for generic bits encapsulated as a module.  What's in this
configuration itself is the stuff that we expect needs to be customized on per-org basis.

## Getting Started

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

### AWS
Follow [`docs/aws/getting-started.md`](../../../docs/aws/getting-started.md) to setup AWS CLI to
authenticate as a user/role which can access/create the AWS account in which you wish to provision
your psoxy instance.


