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

## [v0.4.15](https://github.com/Worklytics/psoxy/releases/tag/v0.4.15)

Changes:
 - you will need a `terraform init`
 - you may see various changes if you used `lookup_table_builders` feature; these are breaking
   changes and your "lookup_table" buckets may be re-built; if this poses a problem for you, contact
   support and we'll help with the `terraform state mv` commands to preserve your existing buckets

eg
```shell
terraform state mv 'module.psoxy.module.psoxy_lookup_tables_builders["lookup-hris"].aws_s3_bucket.output' \ 
  'module.psoxy.module.lookup_output["lookup-hris"].aws_s3_bucket.output' 
```

Fixes:
- the "Lookup" file use-case, supported in our examples through `lookup_table_builders` variable,
  never worked as expected due to S3 limitation, where only 1 of the 2 "event notifications" our
  modules set on a given S3 bucket actually work (race-case as to which). This should be fixed if
  you re-apply your terraform configuration with this version (but bucket will likely be destroyed
  and re-built).


## [v0.4.14](https://github.com/Worklytics/psoxy/releases/tag/v0.4.14)

Changes:
 - heavily refactored `examples`/`examples-dev`; if you're directly using these, rather than having
   made your own copy (as we recommend/describe in 'Getting Started', you may see lots of changes;
   it is NOT recommended you apply these, as some of them will be destructive)
 - you may have to re-install the test tool (`npm install tools/psoxy-test`)
 

## [v0.4.13](https://github.com/Worklytics/psoxy/releases/tag/v0.4.13)

see https://github.com/Worklytics/psoxy/releases/tag/v0.4.13

## [v0.4.12](https://github.com/Worklytics/psoxy/releases/tag/v0.4.12)

see https://github.com/Worklytics/psoxy/releases/tag/v0.4.12

## [v0.4.11](https://github.com/Worklytics/psoxy/releases/tag/v0.4.11)

## v0.4.14




Features:
 - avoid ENV vars for config paths if default values
 - troubleshooting docs
 - support for encryption SSM parameters with AWS KMS keys
 - sanitized examples for outlook-cal, mail
 - improve GCP connector TODOs
 - defensive HTTP header handling, in case unexpected casing from clients
 - npm test tool support for sending the basic health check
 - MSFT connections authenticated via identity federation, rather than certificates
 - Email owners can be specified for MSFT connections
 - support for adding filter by JSON schema to rules
 - npm test tool support for psoxy instances deployed behind API gateway

Fixes:
  - update NPM dependencies to avoid some vulnerabilities (npm packages are only used for testing;
    not exposed to any external clients)

## v0.4.10

Features:
  - `PATH_TO_SHARED_CONFIG`/`PATH_TO_CONNECTOR_CONFIG` fully supported for AWS SSM Parameter Store
     deployments (this includes our standard AWS examples).  By default, these values are `""` and
     `"PSOXY_GCAL"` for usual case; but if you want to add a prefix/path to your SSM parameters,
     you can set Terraform variable `aws_ssm_param_root_path` for our examples. This is useful if
     your AWS account is multipurpose and will hold more than just Proxy deployment (although that
     is not what we recommend, as accounts create implicit security boundaries around your infra; so
     limiting a single account to a single project is a good practice).
        - `v0.4.9` introduced these, but values with `/` didn't work properly due to how AWS SSM
          interprets them; and our terraform examples didn't support setting them
        - Terraform examples still only envision hierarchy, where you locate all your proxy-related
          parameters under a single path (eg, `/corp_it/worklytics/`, with parameters needed by
          all lambdas at that top level; and parameters needed by a specific lambda under a subpath
          named for that lambda, eg `/corp_it/worklytics/PSOXY_GCAL_`; in `v0.5`, we will make these
          distinct.
  - `PATH_TO_SHARED_CONFIG`/`PATH_TO_CONNECTOR_CONFIG` should work in GCP Secret Manager deployments
     too, but is not yet supported throughout our Terraform examples.

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
