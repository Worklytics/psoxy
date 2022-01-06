# Example Terraform Configurations

This directory provides various examples of creation of Proxy instances to support various platforms
and data sources.

A few notes:
  - for collaborative work, we suggest you create a fork of this repo and commit your Terraform
    files, including `terraform.tfvars` and contents of `.terraform/` directory so it can be shared
    with your team (you'll need to modify the `.gitignore` file in this directory to do so)
  - for production use, we suggest you change the Terraform configuration to use a remote backend
    rather than your local file system
