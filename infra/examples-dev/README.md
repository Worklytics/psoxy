# Examples - Dev

Unlike [../examples/](examples/README.md), this directory contains examples intended for development
purposes, referencing Psoxy-provided modules locally, rather than the published versions in GitHub.

Each example includes a `reset-example` symlink to [`tools/reset-example.sh`](../../tools/reset-example.sh) for resetting local IaC state during development (back up / recover `terraform.tfvars`, etc.).
