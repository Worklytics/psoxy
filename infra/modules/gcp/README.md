# GCP Infra

A Terraform module to setup infrastructure required to support Psoxy instances in GCP.

This is the infra common to all instances. An individual instance should later be created on a
"per connection" basis (connection in this context is defined as the abstract concept of ongoing
data import from a data source to a Worklytics account).

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
