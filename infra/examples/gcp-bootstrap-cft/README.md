# Example : gcp-bootstrap-cft

This is an example configuration of [terraform-google-bootstrap](https://registry.terraform.io/modules/terraform-google-modules/bootstrap/google/latest)
- the official, as sanction by Google + Hashicorp, toolkit for this purpose.

Our example exposes a subset of variables to configure; you can edit `main.tf` to set more.

Worklytics offers a somewhat simplified alternative in [`modules/gcp-bootstrap-simple`](../gcp-bootstrap-simple)
which may be sufficient for your purposes - as well as simpler to configure.

We make no endorsement of either as being appropriate for your use-case. Please review both, the
modules on which they depend, and decide what best meets your needs - if any. YMMV

NOTE: this example NOT officially supported by Worklytics.

## Usage

Within this example's directory:

```shell

# use our helper script to create a `terraform.tfvars` file.
./init.sh

# initialize the terraform environment.
terraform init

# authenticate your GCloud CLI, if needed
gcloud auth login

# apply the configuration.
terraform apply

# for collaboration/future work, we recommend you commit the state/lock files to your repo.
# unlike Terraform state files for the non-bootstrap cases, these should *not* contain sensitive
# values such as API keys/secrets.

# a .gitignore file in this directory blocks git from picking up these files. Just rm it and commit

rm .gitignore

git commit
```



