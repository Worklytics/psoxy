# GCP Infra

A Terraform module to setup infrastructure required by a Psoxy instance in GCP.

## Usage
Pre-reqs:
  - a GCP billing account
  - gcloud installed; authenticated as a user who can create projects under the GCP billing account
  - terraform installed

This module is intended to be invoked from a root level module that encodes configuration for an 
environment.  See `../local-dev` for an example.

## Existing Projects

If project already exists, use Terraform import to import it as follows:
```shell

terraform import google_project.psoxy-project {{your_project_id}}
```
