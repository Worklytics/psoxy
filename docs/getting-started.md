# Psoxy Documentation

## FAQs

- [Security](faq-security.md)

### Host Environments

- [AWS](aws/getting-started.md)
- [GCP](gcp/getting-started.md)

### Deployment Environments

Psoxy can be _hosted_ in GCP or AWS, but it can be _deployed_ to those host platforms from a variety
of locations:

- local machine (macOS or a Linux such as Ubuntu likely to be most straightforward options)
- remote VM/container (eg, EC2 instance)
- CI/CD, such as GitHub Actions, Atlassian Bamboo, etc.
- [Terraform Cloud / Terraform Enterprise](guides/terraform-cloud.md)
- Cloud Shell: [aws](aws/cloud-shell.md) (not recommended due to disk size limitation) /
  [gcp](gcp/cloud-shell.md)

## Guides

- [Testing](guides/testing.md) - details about how to test your deployment using our NodeJS-based test
  tooling.
- [Troubleshooting](troubleshooting.md) - some general troubleshooting tips for common issues.
  - [AWS-specific](aws/troubleshooting.md)
  - [GCP-specific](gcp/troubleshooting.md)
- [Cleaning Up](guides/cleaning-up.md) - use Terraform to clean up (destroy) your deployment when you're
  done with it.

### Data Sources

- [Data Sources](sources/README.md)

### Customizing Data Sanitization

- [API Data Sanitization](api-data-sanitization.md)
- [Bulk File Data Sanitization](bulk-file-data-sanitization.md)

### Deployment Migration

If you find yourself needing to migrate a psoxy deployment from one environment to another, such as
from a shared AWS account to a dedicated one, or from AWS--> GCP, etc:

- [Deployment Migration](guides/deployment-migration.md)

Given the complexity and potential pitfalls, we highly recommend you reach out to us for assistance.

## Development

Various topics relevant to anyone developing Psoxy (in Java, or the supporting Terraform
modules/examples) is kept in [`development`](development).

## Alpha Features

See [alpha-features](development/alpha-features/README.md) for details on alpha features.
