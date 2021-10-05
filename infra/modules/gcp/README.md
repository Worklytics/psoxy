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

We recommend using a fresh, dedicated GCP project for all the psoxy instances associated
with a single Worklytics account.  The 'import' scenario is to accommodate use cases where
developer can't create new GCP projects themselves.

If project already exists, use Terraform import to import it as follows:
```shell

terraform import google_project.psoxy-project {{your_project_id}}
```

## Caveats

### Hash Salt
We automatically generate an initial hash salt for you. This is generated with a crypographically
strong random number generator. If you want to change it, you should do so before ever using psoxy
in production, as pseudonyms produced by psoxy will be consistent only for a given salt.

Do NOT change (or rotate) the salt after it's been used to send data to Worklytics; if you do so,
we'll be  unable to match data prior to rotation with data sent after the rotation.

