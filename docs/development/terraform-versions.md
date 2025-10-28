# Terraform Version Compatibility

We use CI to automate testing against *latest* version of each minor version of Terraform
(eg 1.7.x, 1.8.x, etc that we support).

Given that Hashicorp has changed Terraform licensing to ELv2; and that Linux foundation has forked
Terraform from 1.5.x to [create OpenTofu](https://opentofu.org/blog/the-opentofu-fork-is-now-available/),
we are targeting compatibility with 1.7.x feature set.

Do NOT use any features from 1.9.x or later, as we wish to allow all our terraform modules/examples
to work with Terraform 1.7.x minimum, which is vintage 2024.

In particular, **features to NOT use**:
  - `removed` block - introduced in 1.7 (this is our major constraint to maintain compatibility with OpenTofu)
  - `check` block - introduced in 1.5
  - `import` block - introduced in 1.5, but not relevant in use case anyways.
  - `plantimestamp` function - introduced in 1.5
  - `strcontains` function - introduced in 1.5
  - `terraform_data` resource - introduced in 1.4
  - `gcs` backend `kms_encryption_key`, `storage_custom_endpoint` attributes - introduced in 1.4


Features that we don't use as of March 2024, but likely safe:
  - `terraform test` - introduced in 1.6; tests defined in separate .tftest.hcl files, so likely
     defining such won't break compatibility with earlier versions or OpenTofu
  - `quiet` attribute on `local-exec` - introduced in 1.4 ... might be safe if older versions that
    don't know about it just ignore it.


## Testing

To test with a specific version of Terraform, we suggest [tfenv](https://github.com/tfutils/tfenv)
tool. Install everything you need, and create `.terraform-version` file in the root of your
terraform configuration with the desired version.


