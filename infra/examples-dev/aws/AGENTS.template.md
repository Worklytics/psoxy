# AI Agent Guide: Psoxy AWS Example Repository

## What is This Repository?

This is a **Terraform template repository** for deploying the [Worklytics Pseudonymizing Proxy (Psoxy)](https://github.com/Worklytics/psoxy) on **Amazon Web Services (AWS)**. 

Psoxy is a serverless, pseudonymizing Data Loss Prevention (DLP) layer that sits between Worklytics and your organization's data sources (SaaS APIs, cloud storage, etc.). It replaces PII with hash tokens, enabling analysis on anonymized data while enforcing access controls and compliance requirements.

## Purpose

This example repository provides:
- **Pre-configured Terraform modules** that reference the main Psoxy repository
- **Example configurations** for common data sources (Google Workspace, Microsoft 365, Slack, GitHub, etc.)
- **Helper scripts** for initialization, prerequisite checking, and testing
- **Infrastructure-as-code** templates ready for customization

## Key Relationships

- **Main Repository**: [https://github.com/Worklytics/psoxy](https://github.com/Worklytics/psoxy)
  - Contains the core Psoxy Java implementation
  - Provides Terraform modules used by this example
  - Houses documentation and development resources

- **Documentation**: [https://docs.worklytics.co/psoxy](https://docs.worklytics.co/psoxy)
  - Comprehensive deployment guides
  - Configuration reference
  - Troubleshooting and best practices
  - Data source-specific documentation

- **This Example**: A template that customers clone and customize for their AWS deployment

## How This Repository Works

1. **Template Structure**: Customers use this as a GitHub template or clone it to create their own deployment repository
2. **Terraform Modules**: References modules from the main Psoxy repo via Git URLs (e.g., `git::https://github.com/worklytics/psoxy//infra/modules/...`)
3. **Version Pinning**: Each release of this example references a specific version tag of the main Psoxy repository
4. **Customization**: Customers modify `terraform.tfvars` and Terraform files to match their environment and data sources

## Common Tasks for AI Agents

### Understanding the Deployment

- **Read the main README.md** in this repository for human-facing setup instructions
- **Review terraform.tfvars** to understand configuration variables
- **Examine main.tf** to see which modules are being used
- **Check available-connectors** script to see supported data sources

### Helping Users Deploy

1. **Prerequisites**: Guide users to run `./check-prereqs` and install missing tools
2. **Authentication**: Help configure AWS CLI, GCloud CLI (for Google Workspace), or Azure CLI (for Microsoft 365)
3. **Initialization**: Run `./init` to generate `terraform.tfvars` from prompts
4. **Customization**: Help users modify Terraform files to enable/disable data sources
5. **Deployment**: Guide through `terraform plan` and `terraform apply`

### Troubleshooting

- **Reference the main docs**: [https://docs.worklytics.co/psoxy](https://docs.worklytics.co/psoxy)
- **Check AWS-specific docs**: [https://docs.worklytics.co/psoxy/aws/getting-started](https://docs.worklytics.co/psoxy/aws/getting-started)
- **Review Terraform state** and error messages
- **Validate module versions** match the referenced Psoxy release

### Code Navigation

- **Terraform files** (`.tf`) define the infrastructure
- **Helper scripts** (`init`, `check-prereqs`, `available-connectors`) assist with setup
- **Module references** point to the main Psoxy repository at specific version tags
- **Example configurations** show how to enable various data source connectors

## Important Notes

- This is a **template repository** - users should create their own copy, not commit directly to this repo
- **Version compatibility**: The Terraform modules reference specific Psoxy release tags
- **AWS-specific**: This example is for AWS deployments; see `psoxy-example-gcp` for Google Cloud Platform
- **Security**: Users must configure authentication credentials and IAM permissions appropriately
- **Data sources**: Not all connectors are enabled by default; users customize based on their needs

## Getting More Help

- **Documentation**: [https://docs.worklytics.co/psoxy](https://docs.worklytics.co/psoxy)
- **Main Repository Issues**: [https://github.com/Worklytics/psoxy/issues](https://github.com/Worklytics/psoxy/issues)
- **Support**: [sales@worklytics.co](mailto:sales@worklytics.co)

