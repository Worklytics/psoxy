# gcp-bootstrap

This bootstraps infra needed to use Terraform using a GCS bucket as the backend, including:
  - encryption key for the bucket
  - the bucket itself

This is a simplified alternative to [terraform-google-bootstrap](https://registry.terraform.io/modules/terraform-google-modules/bootstrap/google/latest),
which provides more functionality and produces a setup more aligned to GCP best practices - at the
expense of more complexity.

This should be executed by a sufficiently privileged GCP user.  Alternatively, you can use the
Terraform code as a guide to create the resources directly via GCP console.

NOTE: as this is intended to bootstrap GCP infra for storing Terraform state, its own state will
be written to your local file system. You could commit this state to a code repository if you wish,
or, given its simplicity, discard it and re-import in the future if needed.

If you execute this Terraform module again with the same variables as the same user, it should NOT
overwrite or cause any disruptions to the previously existing infrastructure - it will simply error
as the project, bucket, keys, etc already exist (and GCP does not allow names of these to be
re-used). It should be simple enough to use the error messages as guide to `terraform import` all
the pre-existing stuff, and thus reconstitute a state from a prior execution.


## Usage

Create a variables file named `terraform.tfvars` with the following content, customized for your
needs (review `variables.tf` for optional variables that you might find useful to define):

```terraform
project_id=your-project-id
```

Initialize your configuration:
```shell
terraform init
```

If the project you intend to use already exists, import it:
```shell
terraform import google_project.project {{your-project-id}}
```

Apply your Terraform config:
```shell
terraform apply
```

Look for a TODO file which should have been created; this will include the necessary Terraform code
which you should add to the `main.tf` file of your Psoxy terraform config (which will be a different
root module than this one).

You'll need to run another `terraform init` again at the root of your Psoxy terraform config. If you
previously used that config with a backend *other* than this GCS one, that init command should
prompt you to confirm that you wish to move it.
