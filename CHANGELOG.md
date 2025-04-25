# CHANGELOG

Please review [releases](https://github.com/Worklytics/psoxy/releases) for full details of changes
in each release's notes.

Changes to be including in future/planned release notes will be added here.

## Next

## [0.5.2](https://github.com/Worklytics/psoxy/release/tag/v0.5.2)
  - `slack` : discovery rules now allow names of accounts with `is_bot==true` to pass through; are now "default deny" on API fields, returning only data matching expected fields.
- `GitHub Copilot`: Added new connector for supporting fetching Copilot data from GitHub.
- `GitHub`: Rules updated for tokenizing team slug name and redacting URLS not required.
- `Microsoft 365 Copilot`: Adding support for fetching Copilot interactions through `/beta/copilot/users/{id}/interactionHistory/getAllEnterpriseInteractions` endpoint
- `Zoom`:
  - support for fetching meeting summaries, which opens new `/v2/meetings/{meetingId}/meeting_summary` endpoint in proxy rules
  - support for getting settings from meeting details on `/v2/meetings/{meetingId}`


## [0.5.1](https://github.com/Worklytics/psoxy/release/tag/v0.5.1)
- `GitHub`: Added rules for fetching commits through repository and pulls using GraphQL
- `gdirectory` connector: a few redundant oauth scopes removed; no impact on functionality. you can remove these from your DWD grants if you wish, but it's not necessary


## [0.5.0](https://github.com/Worklytics/psoxy/release/tag/v0.5.0)

BREAKING:
  - minimum `azuread` provider version is generally 2.44; if you're using an older version, you'll need to
    upgrade (`terraform init --upgrade`); a state refresh (`terraform refresh`) may help if it complains about unknown attributes
    present in your state
   - `azuread-local-cert` module variables have changed; you must now pass `application_id` instead
    of `application_object_id`; these refer to different values you can obtain via the [Microsoft Entra admin center](https://entra.microsoft.com/#home)
    portal (formally Azure AD portal blade)
  - variables to `aws-host`/`gcp-host` modules to have changed slightly; if you initially copied an
    example based on 0.4.x, you may have to update some variable names in your `main.tf`.
  - minimum `google` provider version is now 5.0; this applies whether you're using GCP-hosted proxy, or merely Google Workspace as a
    data source
  - various migrations applicable to 0.4.x have been removed; if upgrading from 0.4.x, make sure you first upgrade to latest version of 0.4.x (eg, 0.4.61), run
    `terraform apply`, and THEN update to 0.5.x
  - the v0.3 pseudonymization algorithm is no longer supported; attempting to do so should result in an error
  - `scope` field will no longer be sent with JSON-encoded pseudonyms.
  - minimum java version in now 17; java 11 no longer supported (as it's a deprecated runtime in GCP; and Oracle support has ended)

## [0.4.61](https://github.com/Worklytics/psoxy/release/tag/v0.4.61)
 - added some `columnsToPseudonymizeIfPresent` to survey bulk connectors; these are to avoid PII
   being sent to Worklytics if these unexpected columns sent, but without errors in usual case, when
   these are omitted.

## [0.4.60](https://github.com/Worklytics/psoxy/release/tag/v0.4.60)
 - Calendar rules change to allow OOO-related snippets to be passed through event title fields;
    this is required for proper OOO-analysis in Worklytics Calendar 3.0 methodology.
 - MSFT Teams: Support for listing callRecords

## [0.4.58](https://github.com/Worklytics/psoxy/release/tag/v0.4.58)
 - Rules for Outlook Calendar, Outlook Mail and Teams have been updated for *no app id* and *no group id* cases
   to avoid supporting requests with plain user GUIDs instead of pseudonymized.
 - Slack: Including rules for Slack Huddles through *Rooms* as part of conversation history endpoint
 - GitHub: Adding support for [email](https://docs.github.com/en/enterprise-cloud@latest/admin/managing-iam/iam-configuration-reference/saml-configuration-reference#saml-attributes) attribute in SAML response model on GraphQL
 - AWS: you'll see `moved` resources on next apply, relating to refactoring; as well as updating IAM policy that had a wildcard to instead be an explicit list of function ARNs. If you're upgrading from before v0.4.46, you'll see create + destroy instead of move.
 - AWS: if using `MinProvisioner` role from `psoxy-constants` module, we've added a req'd perm to
   that role (to allow tagging lambda functions); without this, `default_tags` option does not work.
 - AWS: bulk-mode ephemeral storage set to 10240 (10GB), raised from free amount of 512MB; this will
   incur small cost. It's billable at $0.0000000309 per GB-second. For most bulk use-cases, files
   are processed in fewer than 30 seconds, and customers process fewer than 10 files per week. So
   expected cost of this is $0.0000927 - less than one-thousandth of a cent.

## [0.4.57](https://github.com/Worklytics/psoxy/release/tag/v0.4.57)
Several changes in this version will result in visible changes during `terraform plan`/`apply`:
- Permission changes on Microsoft 365:
  - `MailboxSettings.Read` permission has been added for directory connectors (`azure-ad`, `entra-id`)
    This will have NO impact until your Admin grants this permission in the Microsoft 365 Admin
    Center; until that happens, Worklytics will continue to NOT retrieve mailbox settings.
  - `OnlineMeetings.Read.All` and `OnlineMeetingArtifact.Read.All` permissions have been removed from `outlook-cal` connector
- `java17` runtime by default (previously, was `java11`); `java11` is still supported by AWS, but
   will be deprecated by GCP in Sept 2024. As of 0.4, proxy code is still compiled for java 11 - so
   if you wish to keep using `java11` runtime, it will work; if you require this, let us know asnd
   we'll expose option to select runtime version in the Terraform module.

## [0.4.56](https://github.com/Worklytics/psoxy/release/tag/v0.4.56)
 - due to refactoring, users of Microsoft connectors may see some moves of resources in Terraform
   plan; these will be no-ops.

## [0.4.55](https://github.com/Worklytics/psoxy/release/tag/v0.4.55)
- For Microsoft Connectors:
  - reference to `msgraph` service principal has been replaced with `data` instead of `terraform` resource.
    For that reason you will see changes in plan related to these resource, but those changes are only related on the resources for Terraform
    state as the kind (*data* from *resource*) has been changed. No change will be done in real Entra ID for that.
  - dropping support for `/beta` endpoints in Microsoft Graph API. All endpoints are now using `/v1.0` version.

## [0.4.53](https://github.com/Worklytics/psoxy/release/tag/v0.4.53)
  - As Microsoft Azure Active Directory has been [renamed](https://learn.microsoft.com/en-us/entra/fundamentals/new-name)
    to Microsoft Entra ID, there is a new connector `msft-entra-id`. Old connector `azure-ad` is deprecated and in case of
    new connection, the new one `msft-entra-id` should be used.

## [0.4.52](https://github.com/Worklytics/psoxy/release/tag/v0.4.52)
  - BREAKING: default behavior for sub-addressing aka "plus addressing" of emails has changed; the
    proxy previously considered these canonically distinct. Now, the proxy will consider these
    canonically equivalent. As we don't expect plus addressing to be used hris or directory data,
    this should have little impact.  Changes will most likely be in a few edge cases, such as
    emails or calendar invites sent to a sub-address - sender unlikely to be a subaddress, but
    recipient could be.  In such cases, behavior prior to 0.4.52 would cause recipient to appear
    as a distinct mailbox; from 0.4.52 onward, they will be considered the same mailbox; we expect
    this to be behavior that is more in line with user expectations, so although technically
    breaking, we're introducing it without a major version bump.
  - there new option to enable less strict email canonicalization; we strongly recommend new
    customers to enable it, although it is not enabled by default to avoid a breaking change. Set
    `email_canonicalization` to `IGNORE_DOTS` to enable this feature.
  - BREAKING for examples: default value fore `email_canonicalization` in our example repos has been
    set to `IGNORE_DOTS`; if you've previously forked an example, this is not a breaking change. but
    if you fork an example > 0.4.52 and are attempting to migrate a proxy deployment initially built
    with modules or examples from < 0.4.52, you should explicitly add `email_canonicalization = "STRICT"`
    in your `terraform.tfvars`
  - GCP: Existing GCP functions are using *Container Registry* for building their internal docker image where the psoxy code is deployed. However,
    this is [deprecated since May 2023 and starting Feb 2024](https://cloud.google.com/container-registry/docs/deprecations/container-registry-deprecation) it
    is required that functions use *Artifact Registry* instead. All deployments made since this version will use *Artifact Registry*
    default repository for storing all psoxy images. Any previous version before this version will work without any issue.

## [0.4.51](https://github.com/Worklytics/psoxy/release/tag/v0.4.51)
 - GCP: non-breaking, but noticeable in Terraform plan: `title` attribute of GCP Custom Project
   roles created by our modules are changing to more closely follow conventions GCP uses for its
   built-in roles; as well as prefixing them with your environment ID to group them together
   alphabetically and differentiate in shared project.

## [0.4.50](https://github.com/Worklytics/psoxy/release/tag/v0.4.50)
  - `todos_as_local_files` properly respected now; if you had it as `false`, you may see some local
    files deleted on your next `terraform apply`.
  - to accommodate this fix, many people will see moves of local_file resources, to append `[0]` to
    the resource ids
  - BREAKING - for AWS Secrets Manager (released in 0.4.47 as 'alpha' feature), these will now
    be prefixed by default with the environment ID, unless a `aws_secrets_manager_path` is set.

## [0.4.48](https://github.com/Worklytics/psoxy/release/tag/v0.4.48)
  - BREAKING - GitHub Enterprise Server: authentication strategy has changed; you will see creation
    and destruction of some secrets that are used for authentication; you MUST generate new auth
    credentials for your proxy instance. see [`docs/sources/github/README.md`](docs/sources/github/README.md)
    or contact support for assistance.

## [0.4.47](https://github.com/Worklytics/psoxy/release/tag/v0.4.47)
  - AWS: some moved resources due to refactoring to accommodate option to use AWS Secrets Manager

possibility of breaking due to no longer inferring global SSM parameter prefix?


## [0.4.46](https://github.com/Worklytics/psoxy/release/tag/v0.4.46)
  - you'll see several `timestamp_static` resources provisioned by terraform; these are simply
    timestamps persisted into state. various example API calls in TODOs/tests are derived from these.
    using persistent value avoids showing changes on every plan/apply.
  - AWS:
    - you'll see encryption config for buckets created by the proxy DESTROYED in your plan. This is
      actually a no-op, as these were actually just setting default encryption; per Terraform docs,
      destroying an `aws_s3_bucket_server_side_encryption_configuration` resource resets the bucket
      to Amazon S3 default encryption. See: https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket_server_side_encryption_configuration
    - this change enables configuration of more sophisticated encryption scenarios via composition.
      see [docs/aws/encryption-keys.md](docs/aws/encryption-keys.md) for more details.


## [0.4.44](https://github.com/Worklytics/psoxy/release/tag/v0.4.44)
* Microsoft 365 - Outlook calendar: new scopes for fetching Online Meetings have been added to the Entra ID Application
  used for Microsoft 365 Outlook Calendar and the proxy connector.
  A Microsoft 365 with admin rights for Entra ID will need to grant admin consent
  for `OnlineMeetings.Read.All` and `OnlineMeetingArtifact.Read.All` permissions.

* GitHub Enterprise Server: variable `github_api_host` is marked as deprecated and will be removed
  in next major version. Use `github_enterprise_server_host` instead.


## [0.4.43](https://github.com/Worklytics/psoxy/release/tag/v0.4.43)
 * if you're using the NodeJS test tool, it will be re-installed on your next `terraform apply` due
   to a dependency change.

## [0.4.41](https://github.com/Worklytics/psoxy/release/tag/v0.4.41)
  * GCP only : Compute Engine API will be enabled in the project. Newer versions of GCP terraform
    provider seem to require this. You may see this in your next `terraform plan`, although it may
    also be a no-op if you already have the API enabled.

## [0.4.36](https://github.com/Worklytics/psoxy/release/tag/v0.4.36)
  * Microsoft 365 - Azure AD Directory - default rules change to return `proxyAddresses` field for
    users, pseudonymized; needed to match user's past email addresses against other data sources

## [0.4.34](https://github.com/Worklytics/psoxy/release/tag/v0.4.34)
  * AWS Only: you may see System Manager Parameter description changes; these have no functional
    purpose, just helping provide guidance on function of different secrets.

## [0.4.33](https://github.com/Worklytics/psoxy/release/tag/v0.4.33)
Changes that may appear in Terraform plans:
 * GCP only: secrets that are managed outside of Terraform will no longer be bound as part of the
   cloud function's environment variables. You will see env changes in some cases, as well as 'moves'
   of the associated IAM grants to make those secrets accessible to the cloud function, as this moves
   up one level in module hierarchy
 * AWS only: removing CORS from lambda urls - not necessary

## [0.4.31](https://github.com/Worklytics/psoxy/release/tag/v0.4.31)

Changes:
  * due to split of Terraform vs externally-managed secret values. expect:
    - AWS hosted: many moves of AWS Systems Manager Parameter Store parameters. No parameters
      should be created or destroyed.
    - GCP hosted: destruction of GCP Secret versions for externally managed secrets;
      behavior is now that *no* version for such secret will be provisioned by Terraform,
      instead of one with a placeholder value
    - see https://github.com/Worklytics/psoxy/pull/419
  * changes to GCP secret permissions for "writable" secrets. Psoxy instances will need to disable
    old versions of secrets as they rotate, so no require `secretVersionManager` role instead of
    `secretVersionAdder` role granted previously. See https://github.com/Worklytics/psoxy/pull/447

## [0.4.25](https://github.com/Worklytics/psoxy/releases/tag/v0.4.25)


Changes:
  * `environment_name`/`instance_id` REQUIRED for all of `aws-psoxy-*` modules, taking the place of `function_name`; this is only breaking change
    if your Terraform configuration is directly invoking one of these; `modular-examples` have
    been updated to expect it
  * due to refactoring of IAM policy/role names, you may see MANY replacements of these resources in
    AWS; these are just name changes to better group your infrastructure, so should be no-ops
  *


## [0.4.20](https://github.com/Worklytics/psoxy/releases/tag/v0.4.20)

Due to module refactoring, you will need a `terraform init`.

Changes:
  * you may see grants of the role `iam.serviceAccountKeyAdmin` on individual GCP service accounts
    in your Terraform plan. These grants are required to allow terraform to create/manage service
    account keys, which is only needed for service accounts connecting to Google Workspace APIs.
    Previously, this role was a pre-req expected to be granted at the project-level; this more
    granular approach improves security. If you previously granted `iam.serviceAccountKeyAdmin` at
    a GCP Project Level, you can now remove this.


## [v0.4.18](https://github.com/Worklytics/psoxy/releases/tag/v0.4.18)

Changes:
  * if you didn't copy/fork an example you found in `examples-dev` or `examples`, the default
    variable values in those examples have changed and you may see `PSEUDONYMIZE_APP_IDS=true` in
    changes at your Terraform apply. If you have begun production use of your proxy, please warn
    `support@worklytics.co` if you apply this change.

## [v0.4.17](https://github.com/Worklytics/psoxy/releases/tag/v0.4.17)

Changes:
  * you may see bucket lifecycle rules set in your next `terraform apply`; if you wish to customize
    these values, review https://github.com/Worklytics/psoxy/pull/308 https://github.com/Worklytics/psoxy/pull/310

## [v0.4.16](https://github.com/Worklytics/psoxy/releases/tag/v0.4.16)

Highlights:
  * support re-writing bulk object path prefixes when processing https://github.com/Worklytics/psoxy/pull/301
  * release tooling https://github.com/Worklytics/psoxy/pull/300
  * test tool bulk input output base paths https://github.com/Worklytics/psoxy/pull/302
  * use object metadata to avoid potential loop if bulk proxy writes back to input bucket https://github.com/Worklytics/psoxy/pull/303
  * make AWS role optional in test tool https://github.com/Worklytics/psoxy/pull/297


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

# Planned

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