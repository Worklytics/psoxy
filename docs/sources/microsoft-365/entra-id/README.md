# Entra ID

Connect to Directory data in Microsoft 365. This allows enumeration of all users, groups, and group
members in your organization, to provide additional segmentation, timezone/workday information, etc.

## Required Scopes
- [`User.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#userreadall)
- [`Group.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#userreadall)

## Authentication

See the [Microsoft 365 Authentication](../README.md#authentication) section of the main README.

## Authorization

See the [Microsoft 365 Authorization](../README.md#authorization) section of the main README.

## Example Data

| API Endpoint | Example Response                                                             | Sanitized Example Response |
| --- |------------------------------------------------------------------------------| --- |
| `/v1.0/groups/{group-id}/members` | [original/group-members.json](example-api-responses/original/group-members.json) | [sanitized/group-members.json](example-api-responses/sanitized/group-members.json) |
| `/v1.0/users` | [original/users.json](example-api-responses/original/users.json)             | [sanitized/users.json](example-api-responses/sanitized/users.json) |
| `/v1.0/users/me` | [original/user.json](example-api-responses/original/user.json)              | [sanitized/user.json](example-api-responses/sanitized/user.json) |
| `/v1.0/groups` | [original/groups.json](example-api-responses/original/groups.json)          | [sanitized/groups.json](example-api-responses/sanitized/groups.json) |


Assuming proxy is auth'd as an application, you'll have to replace `me` with your MSFT ID or
`UserPrincipalName` (often your email address).

See more examples in the `docs/sources/microsoft-365/entra-id/example-api-responses` folder
of the [Psoxy repository](https://github.com/Worklytics/psoxy).

## Sanitization Rule Examples

- [Default Rules](entra-id.yaml)
- [Rules, pseudonymizing MSFT account IDs](entra-id_no-app-ids.yaml)
- [Rules, pseudonymizing MSFT account IDs](entra-id_no-app-ids_no-orig.yaml)

