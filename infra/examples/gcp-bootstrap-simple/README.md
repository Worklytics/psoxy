# Example: gcp-bootstrap-simple

This is a simple example of bootstrapping a GCP project for Terraform use, creating a GCS bucket
in which a Terraform state, which may contain sensitive information such as API keys/secrets, can
be stored.

By default, this configuration will provision
  - a GCP project
  - a Cloud KMS encryption key
  - a GCS bucket, encrypted with the key
  - create a TODO file on your local filesystem with example usage of those resources as a backend
    for a Terraform configuration.

An alternative approach relies on a similar module maintained by Google: [gcp-bootstrap-cft](../gcp-bootstrap-cft).

NOTE: this example NOT officially supported by Worklytics. YMMV.

## Usage

Within this example's directory:

```shell

# use our helper script to create a `terraform.tfvars` file.
./init.sh

# initialize the terraform environment.
terraform init

# authenticate your GCloud CLI, if needed
gcloud auth login

# if the GCP project you wish to use already exists, import it
terraform import module.gcp_bootstrap.google_project.project {{your-project-id}}`

# apply the configuration.
terraform apply

# review the TODO file
# this will include the necessary Terraform code which you should add to the `main.tf` file of your
# Psoxy terraform config (which will be a different root configuration than this one).
cat 'TODO - terraform backend.md'

# for collaboration/future work, we recommend you commit the state/lock files to your repo.
# unlike Terraform state files for the non-bootstrap cases, these should *not* contain sensitive
# values such as API keys/secrets.

# a .gitignore file in this directory blocks git from picking up these files. Just rm it and commit

rm .gitignore

git commit
```




