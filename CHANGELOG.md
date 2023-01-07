# CHANGELOG

Working tracking of changes, updated as work done prior to release.  Please review [releases](https://github.com/Worklytics/psoxy/releases) for ultimate versions.


## Next

## 0.5 *future, subject to change!!*
  - RULES only via config management, never env variable
  - Eliminate "fall-through" configs.
     - `PATH_TO_SHARED_CONFIG` - env var that locates shared parameters within the config store.
     - `PATH_TO_CONNECTOR_CONFIG` - env var that locates connector-specific parameters within the
       config store.
  - Expect distinct paths for the shared and connector scopes, to support more  straight-forward IAM
    policies.
     - eg, `PSOXY_SHARED` and `PSOXY_GCAL`, to allow IAM policies such as "read `PSOXY_SHARED*`" and
        "read+write `PSOXY_GCAL*`" (if shared secrets have common prefix with connector secrets,
        then wildcard policy to read shared also grants read of secrets across all connectors)
  - keys/salts per value kind (PII, item id, etc)


## v0.4.9

Breaking Changes: (at least, somewhat breaking)
- Behavior of `aws-ssm-secrets` module changed such that changes to parameter values will NOT be
  ignored. If you've changed these values outside of Terraform, you should use a `terraform import`
  to get your value and potentially modify Terraform config.  (this convenience feature allowed
  users to directly fill SSM secret values, while avoid overwriting them with Terraform defaults;
  but gave false sense that the new values won't be persisted in your Terraform state - they will.
  Always treat your Terraform state as equivalently sensitive to any of these secrets, such as API
  keys.)
- the base examples (`aws-google-workspace`, `gcp-google-workspace`) no longer provision GCP
  project; they depend on it already existing. If you're directly using either of these examples,
  you MUST avoid destroying the Google project by doing one of the following before you next
  Terraform apply:
    - aws-google-workspace : `terraform state rm google_project.psoxy-google-connectors`
    - gcp-google-workspace : `terraform state rm google_project.psoxy-project`

Features:
- Updated GCP example with support for using secrets and specs from connectors module and for using
  and refreshing authentication tokens from GCP SecretManager.
- From `gcp` module, output variables `salt_secret_id` and `salt_secret_version_number` have been
  marked as deprecated,  and they will be removed on next version. Instead, use `secrets` output
  variable with the right secrets to use when populating the function.
- For same reason, `gcp-psoxy-rest` will not use `salt_secret_id` and `salt_secret_version_number`
  input variables, they are going to be  dropped in next version. Use `secret_bindings` instead
  for providing any secret that needs to be used by the function.
- values passed for GCP folder ID, GCP org ID, GCP billing account to the examples
  (`aws-google-workspace`, `gcp-google-workspace`) are now ignored and will be removed in next
  major version; but if you currently pass values for these, nothing actually break. Just note
  that changing them will not update these attributes of your GCP project, as per above it's no
  longer managed by Terraform.

## v0.4.8

  - v0.4.8 introduces simplified examples, with a single cloud module dependency. if your Terraform
    configuration is based on an example from an earlier version, and you wish to migrate to this
    new structure, append contents of `migration-v0.4.8.tf` to your `main.tf` and apply it. You can
    revert this change after one successful apply.
  - AWS IAM roles/policies have been renamed; you may see many deletes/creates, but should be no
    effective changes
  - an unneeded SSM Parameter AWS policy has been removed; it is superceded by more granular policies
    created in v0.4.6

## v0.4.7

Upgrade Notes:
  - secret management has been refactored; you may see indications of some secrets being moved, or
    even destroyed and recreated. If you plan shows SALT or ENCRYPTION_KEY as being destroyed,
    **DO NOT** apply the plan and contact Worklytics support for assistance.

