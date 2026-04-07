# Terraform Version Compatibility

We use CI to automate testing against *latest* version of each minor version of Terraform
(eg 1.7.x, 1.8.x, etc that we support).

Given that Hashicorp has changed Terraform licensing to ELv2; and that Linux foundation has forked
Terraform from 1.5.x to [create OpenTofu](https://opentofu.org/blog/the-opentofu-fork-is-now-available/),
we are targeting compatibility with 1.7.x feature set. OpenTofu 1.7 tracks with Terraform 1.7 functionality.

Do NOT use any features from 1.8.x or later, as we wish to allow all our terraform modules/examples
to work with Terraform back to 1.7.x, which is vintage Jan 2024.

In particular, **features to NOT use**:
  - `provider` functions - introduced in 1.8
  - cross-variable referencing in `validation` block conditions - introduced in 1.9

Features that you CAN safely use now (>= 1.7.x):
  - `removed` block - introduced in 1.7
  - `mock_provider` in tests - introduced in 1.7
  - `terraform test` - introduced in 1.6
  - `check` block - introduced in 1.5
  - `import` block - introduced in 1.5


## Testing

To test with a specific version of Terraform, we suggest [tfenv](https://github.com/tfutils/tfenv)
tool. Install everything you need, and create `.terraform-version` file in the root of your
terraform configuration with the desired version.


