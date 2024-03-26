# Terraform Version Compatibility

We use CI to automate testing against *latest* version of each minor version of Terraform
(eg 1.3.x, 1.4.x, etc that we support).

Given that Hashicorp has changed Terraform licensing to ELv2; and that Linux foundation has forked
Terraform from 1.5.x to [create OpenTofu](https://opentofu.org/blog/the-opentofu-fork-is-now-available/),
we are targeting compatibility with 1.6.x feature set.

Do NOT use any features from 1.7.x or later, as we wish to allow all our terraform modules/examples
to work with Terraform back to 1.3.x, which is vintage Sept 2022.


## Testing
To test with a specific version of Terraform, we suggest [tfenv](https://github.com/tfutils/tfenv)
tool. Install everything you need, and create `.terraform-version` file in the root of your
terraform configuration with the desired version.


