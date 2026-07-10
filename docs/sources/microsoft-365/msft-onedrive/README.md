# Microsoft OneDrive

**Connector ID:** `msft-onedrive`

**Availability:** Alpha

Connect Microsoft OneDrive data to Worklytics, enabling analysis of collaboration and work
happening in files stored in OneDrive (personal drives and group document libraries). Uses the
Microsoft Graph `delta` feed to enumerate created/edited/deleted `driveItem`s for each user/group
drive, and per-file version history to identify real edit events.

Please review the [Microsoft 365 README](../README.md) for general information applicable to
all Microsoft 365 connectors. This connector also requires a working connection to
[Microsoft Entra ID](../entra-id/README.md), used to enumerate the users and groups whose OneDrive
content is polled.

## Endpoints Used

| Endpoint                                                    | Purpose                                                                            |
|--------------------------------------------------------------|-------------------------------------------------------------------------------------|
| `GET /v1.0/users/{userId}/drive/root/delta`                   | Enumerate created/edited/deleted files and folders in a user's OneDrive.            |
| `GET /v1.0/groups/{groupId}/drive/root/delta`                 | Enumerate created/edited/deleted files and folders in a group's default document library. |
| `GET /v1.0/sites/{siteId}/drive/root/delta`                   | Enumerate created/edited/deleted files and folders in a SharePoint site's default document library. |
| `GET /v1.0/users/{userId}/drive/items/{itemId}/versions`      | Per-file edit history (real actor + timestamp per edit), for files owned by a user. |
| `GET /v1.0/groups/{groupId}/drive/items/{itemId}/versions`    | Per-file edit history, for files owned by a group.                                  |
| `GET /v1.0/sites/{siteId}/drive/items/{itemId}/versions`      | Per-file edit history, for files owned by a SharePoint site.                        |

Also requires `GET /v1.0/users` and `GET /v1.0/groups` (via the Microsoft Entra ID connection) to
enumerate the owners whose drives are polled.

## Required Scopes

- [`Files.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#filesreadall) - read files in all users', groups', and sites' drives (the `delta` and `versions` endpoints above)
- [`User.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#userreadall) - enumerate users to poll
- [`Group.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#groupreadall) - enumerate groups to poll

## Authentication

See the [Microsoft 365 Authentication](../README.md#authentication) section of the main README.

## Authorization

See the [Microsoft 365 Authorization](../README.md#authorization) section of the main README.

## Example Data

| API Endpoint                                              | Example Response                                                                                     | Sanitized Example Response                                                                              |
|-------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `/v1.0/users/{userId}/drive/root/delta`                      | [original/Users_drive_delta_v1.0.json](example-api-responses/original/Users_drive_delta_v1.0.json)       | [sanitized/Users_drive_delta_v1.0.json](example-api-responses/sanitized/Users_drive_delta_v1.0.json)       |
| `/v1.0/users/{userId}/drive/items/{itemId}/versions`         | [original/Drive_items_versions_v1.0.json](example-api-responses/original/Drive_items_versions_v1.0.json) | [sanitized/Drive_items_versions_v1.0.json](example-api-responses/sanitized/Drive_items_versions_v1.0.json) |

The `groups/{groupId}/...` and `sites/{siteId}/...` variants of the endpoints above return the
same response shapes as their `users/{userId}/...` counterparts, and are sanitized identically.

## Examples

- [Example Rules](msft-onedrive.yaml)
