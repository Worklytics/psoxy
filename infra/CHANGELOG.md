
# Change Log

## v0.4.9

  - Updated GCP example with support for using secrets and specs from connectors module and for using
    and refreshing authentication tokens from GCP SecretManager.
  - From `gcp` module, output variables `salt_secret_id` and `salt_secret_version_number` have been marked as deprecated, 
    and they will be removed on next version. Instead, use `secrets` output variable with the right secrets to use when
    populating the function.
  - For same reason, `gcp-psoxy-rest` will not use `salt_secret_id` and `salt_secret_version_number` input variables, they are going to be
    dropped in next version. Use `secret_bindings` instead for providing any secret that needs to be used by the function

## v0.4.8

  - v0.4.8 introduces simplified examples, with a single cloud module dependency. if your Terraform
    configuration is based on an example from an earlier version, and you wish to migrate to this
    new structure, append contents of `migration-v0.4.8.tf` to your `main.tf` and apply it. You can
    revert this change after one successful apply.