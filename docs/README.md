# Psoxy Documentation

## FAQs

 - [Security](faq-security.md)

### Host Environments

  - [AWS](aws/getting-started.md)
  - [GCP](gcp/getting-started.md)


### Deployment Environments
Psoxy can be *hosted* in GCP or AWS, but it can be *deployed* to those host platforms from a variety
of locations.

  - CI/CD, such as GitHub Actions, Atlassian Bamboo, etc.
  - [Terraform Cloud / Terraform Enterprise](terraform-cloud.md)
  - Cloud Shell: [aws](aws/cloud-shell.md) (not recommended) / [gcp](gcp/cloud-shell.md)

## Guides

  - [Testing](testing.md) - details about how to test your deployment using our NodeJS-based test tooling.
  - [Troubleshooting](troubleshooting.md) - some general troubleshooting tips for common issues.
      - [AWS-specific](aws/troubleshooting.md)
      - [GCP-specific](gcp/troubleshooting.md)

### Data Sources

 - [sources](sources)

### Customizing Data Sanitization

  - [API Data Sanitization](api-data-sanitization.md)
  - [Bulk File Data Sanitization](bulk-file-data-sanitization.md)

### Deployment Migration

If you find yourself needing to migrate a psoxy deployment from one environment to another, such as
from a shared AWS account to a dedicated one, or from AWS--> GCP, etc:

  - [Deployment Migration](deployment-migration.md)

Given the complexity and potential pitfalls, we highly recommend you reach out to us for assistance.


## Development

Various topics relevant to anyone developing Psoxy (in Java, or the supporting Terraform modules/examples)
is kepte in [`./development`](./development).




