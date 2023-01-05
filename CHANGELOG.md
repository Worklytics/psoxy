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


## v0.4.8

  - examples have been refactored; old
  - AWS IAM roles/policies have been renamed; you may see many deletes/creates, but should be no
    effective changes
  - an unneeded SSM Parameter AWS policy has been removed; it is superceded by more granular policies
    created in v0.4.6

## v0.4.7

Upgrade Notes:
  - secret management has been refactored; you may see indications of some secrets being moved, or
    even destroyed and recreated. If you plan shows SALT or ENCRYPTION_KEY as being destroyed,
    **DO NOT** apply the plan and contact Worklytics support for assistance.

