# Microsoft OneDrive

**Connector ID:** `msft-onedrive`

**Availability:** Alpha

Connect Microsoft OneDrive data to Worklytics, enabling analysis of collaboration and work
happening in files stored in OneDrive (personal drives) and SharePoint document libraries (group
drives). Enumerates the real drive(s) owned by each user/group (a group's SharePoint team site can
have more than one document library, not just a single default drive), then uses the Microsoft
Graph `delta` feed to identify created/edited/deleted files and folders in each drive, and per-file
version + activity history to identify real edit/share/delete/move/rename/restore/comment/mention
events.

Please review the [Microsoft 365 README](../README.md) for general information applicable to
all Microsoft 365 connectors.

## Endpoints Used

| Endpoint                                                       | Purpose                                                                                            |
|-----------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| `GET /v1.0/users`                                                | Enumerate the users whose OneDrive is polled.                                                        |
| `GET /v1.0/groups`                                               | Enumerate the groups whose SharePoint document libraries are polled.                                 |
| `GET /v1.0/users/{userId}/drives`                                | List a user's drive(s) (a user usually has one, but system-managed drives are included).             |
| `GET /v1.0/groups/{groupId}/drives`                              | List a group's drive(s) (a group's SharePoint team site can have multiple document libraries).       |
| `GET /v1.0/drives/{driveId}/root/delta`                          | Enumerate created/edited/deleted files and folders in a drive.                                       |
| `GET /v1.0/drives/{driveId}/items/{itemId}/versions`             | Per-file edit history (real actor + timestamp per edit).                                             |
| `GET /v1.0/drives/{driveId}/items/{itemId}/activities`           | Per-file recent activity (create/edit/delete/move/rename/restore/share/comment/mention, by whom, and when). |
| `GET /v1.0/drives/{driveId}/activities`                          | Drive-wide recent activity feed (same shape as the per-file activity feed, but not scoped to a single item). |

**Known limitation:** SharePoint site drives (`/v1.0/sites/{siteId}/...`) are not yet enumerated by
this connector; only users' and groups' drives are covered.

## Required Scopes

- [`User.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#userreadall) - enumerate users to poll
- [`Group.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#groupreadall) - enumerate groups to poll
- [`Files.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#filesreadall) - list drives and read file metadata, version history, and recent activity in users'/groups' drives

## Authentication

See the [Microsoft 365 Authentication](../README.md#authentication) section of the main README.

## Authorization

See the [Microsoft 365 Authorization](../README.md#authorization) section of the main README.

## Example Data

| API Endpoint                                        | Example Response                                                                            | Sanitized Example Response                                                                     |
|-------------------------------------------------------|-------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| `/v1.0/users`                                          | [original/users.json](example-api-responses/original/users.json)                               | [sanitized/users.json](example-api-responses/sanitized/users.json)                             |
| `/v1.0/groups`                                         | [original/groups.json](example-api-responses/original/groups.json)                              | [sanitized/groups.json](example-api-responses/sanitized/groups.json)                           |
| `/v1.0/users/{userId}/drives`                          | [original/list_drives.json](example-api-responses/original/list_drives.json)                    | [sanitized/list_drives.json](example-api-responses/sanitized/list_drives.json)                 |
| `/v1.0/groups/{groupId}/drives`                        | [original/list_drives.json](example-api-responses/original/list_drives.json)                    | [sanitized/list_drives.json](example-api-responses/sanitized/list_drives.json)                 |
| `/v1.0/drives/{driveId}/root/delta`                    | [original/get_drive_delta.json](example-api-responses/original/get_drive_delta.json)             | [sanitized/get_drive_delta.json](example-api-responses/sanitized/get_drive_delta.json)          |
| `/v1.0/drives/{driveId}/items/{itemId}/versions`       | [original/list_driveItemVersion.json](example-api-responses/original/list_driveItemVersion.json) | [sanitized/list_driveItemVersion.json](example-api-responses/sanitized/list_driveItemVersion.json) |
| `/v1.0/drives/{driveId}/items/{itemId}/activities`     | [original/list_itemActivity.json](example-api-responses/original/list_itemActivity.json)         | [sanitized/list_itemActivity.json](example-api-responses/sanitized/list_itemActivity.json)     |
| `/v1.0/drives/{driveId}/activities`                    | [original/list_driveActivity.json](example-api-responses/original/list_driveActivity.json)       | [sanitized/list_driveActivity.json](example-api-responses/sanitized/list_driveActivity.json)   |

See more examples in the `docs/sources/microsoft-365/msft-onedrive/example-api-responses` folder of
the [Psoxy repository](https://github.com/Worklytics/psoxy).

## Example Rules

- [Example Rules](msft-onedrive.yaml)
- [Example Rules: no App IDs](msft-onedrive_no-app-ids.yaml)
