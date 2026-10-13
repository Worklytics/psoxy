# Google Workspace&trade; Directory

**Connector ID:** `gdirectory`

**Availability:** GA

Please review the [Google Workspace&trade; README](../README.md) for general information applicable to
all Google Workspace connectors.

## Required OAuth Scopes

- `admin.directory.user.readonly`
- `admin.directory.domain.readonly`
- `admin.directory.group.readonly`
- `admin.directory.orgunit.readonly`

For Domain-wide Delegation in the Google Workspace Admin console, paste the following comma-separated list into the **Scopes** field:

```
https://www.googleapis.com/auth/admin.directory.user.readonly,https://www.googleapis.com/auth/admin.directory.domain.readonly,https://www.googleapis.com/auth/admin.directory.group.readonly,https://www.googleapis.com/auth/admin.directory.orgunit.readonly
```

## Required GCP APIs

Enable the following API in the GCP project where you provision the OAuth client:

- `admin.googleapis.com` (Admin SDK API)

## Examples

- [Example Rules](directory.yaml)
- [Example Rules (no App IDs)](directory_no-app-ids.yaml)
- Example Data:
  - [original/group-members.json](example-api-responses/original/group-members.json) |
    [sanitized/group-members.json](example-api-responses/sanitized/group-members.json)
  - [original/user.json](example-api-responses/original/user.json) |
    [sanitized/user.json](example-api-responses/sanitized/user.json)

Example API call path parameters: `{USER_ID}` is a Directory user email or user id from `GET .../users` (YAML `{accountId}`). `{GROUP_ID}` is a group email or group id from `GET .../groups`. `{ORG_UNIT_PATH}` is an org-unit path from `GET .../orgunits` (for example `Engineering`). YAML `{customerId}` is `my_customer` in the generated examples.

See more examples in the `docs/sources/google-workspace/directory/example-api-responses` folder
of the [Psoxy repository](https://github.com/Worklytics/psoxy).


---
Google Workspace&trade; and related marks are trademarks of Google LLC.
Worklytics&trade; is a trademark of Worklytics, Corp.
