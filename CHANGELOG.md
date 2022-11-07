# CHANGELOG

Working tracking of changes, updated as work done prior to release.  Please review [releases](https://github.com/Worklytics/psoxy/releases) for ultimate versions.


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


