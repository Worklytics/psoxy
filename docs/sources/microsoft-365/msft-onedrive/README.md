# Microsoft OneDrive

**Connector ID:** `msft-onedrive`

**Availability:** Beta

Connect Microsoft OneDrive data to Worklytics, enabling analysis of collaboration and work happening in files stored in OneDrive (personal drives) and SharePoint document libraries (group drives). Enumerates the real drive(s) owned by each user/group (a group's SharePoint team site can have more than one document library, not just a single default drive), then uses the Microsoft Graph `delta` feed to identify created/edited/deleted files and folders in each drive, and per-file activity history to identify real edit/share/delete/move/rename/restore/comment/mention events.

Please review the [Microsoft 365 README](../README.md) for general information applicable to all Microsoft 365 connectors.

## Endpoints Used

| Endpoint                                                       | Purpose                                                                                            |
|-----------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| `GET /v1.0/users`                                                | Enumerate the users whose OneDrive is polled.                                                        |
| `GET /v1.0/groups`                                               | Enumerate the groups whose SharePoint document libraries are polled.                                 |
| `GET /v1.0/users/{userId}/drives`                                | List a user's drive(s) (a user usually has one, but system-managed drives are included).             |
| `GET /v1.0/groups/{groupId}/drives`                              | List a group's drive(s) (a group's SharePoint team site can have multiple document libraries).       |
| `GET /v1.0/drives/{driveId}/root/delta`                          | Enumerate created/edited/deleted files and folders in a drive.                                       |
| `GET /v1.0/drives/{driveId}/items/{itemId}/activities`           | Per-file recent activity (create/edit/delete/move/rename/restore/share/comment/mention, by whom, and when). |
| `GET /v1.0/drives/{driveId}/activities`                          | Drive-wide recent activity feed (same shape as the per-file activity feed, but not scoped to a single item). |

**Known limitation:** SharePoint site drives (`/v1.0/sites/{siteId}/...`) are not yet enumerated by this connector; only users' and groups' drives are covered.

## Required Scopes

- [`User.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#userreadall) - enumerate users to poll
- [`Group.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#groupreadall) - enumerate groups to poll
- [`Files.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#filesreadall) - list drives and read file metadata and recent activity in users'/groups' drives

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
| `/v1.0/drives/{driveId}/items/{itemId}/activities`     | [original/list_itemActivity.json](example-api-responses/original/list_itemActivity.json)         | [sanitized/list_itemActivity.json](example-api-responses/sanitized/list_itemActivity.json)     |
| `/v1.0/drives/{driveId}/activities`                    | [original/list_driveActivity.json](example-api-responses/original/list_driveActivity.json)       | [sanitized/list_driveActivity.json](example-api-responses/sanitized/list_driveActivity.json)   |

See more examples in the `docs/sources/microsoft-365/msft-onedrive/example-api-responses` folder of
the [Psoxy repository](https://github.com/Worklytics/psoxy).

### Populating example API calls with real IDs

By default, the `{EXAMPLE_MSFT_GROUP_GUID}`/`{EXAMPLE_MSFT_ONEDRIVE_DRIVE_ID}`/`{EXAMPLE_MSFT_ONEDRIVE_ITEM_ID}` segments of this connector's example test calls (generated as part of your Terraform deployment) are left as placeholders, since Terraform has no way to enumerate real values from your tenant.

Rather than hunting for these by hand, run `tools/psoxy-test/find-msft-onedrive-example-values.js` against your deployed connector; it will find a real drive (checking users, then groups) and a real driveItem from that drive's `root/delta` feed for you. See [Psoxy test tool](../../../guides/psoxy-test-tool.md#microsoft-onedrive-finding-a-drive-and-drive-item).

To have real, directly-runnable example calls generated by Terraform instead, set the following keys in the `msft_365_connector_settings` Terraform variable:

| Key                              | Value                                                                                                    |
|------------------------------------|--------------------------------------------------------------------------------------------------------|
| `example_msft_group_guid`          | id (GUID) of a group whose SharePoint document library you want to use as an example, from `GET /v1.0/groups` |
| `msft_onedrive_example_drive_id`   | a `Drive.id` from that group's (or a user's) `GET .../drives` response                                   |
| `msft_onedrive_example_item_id`    | a `driveItem.id` from that drive's `GET /v1.0/drives/{driveId}/root/delta` response                      |

e.g., in your `terraform.tfvars`:
```hcl
msft_365_connector_settings = {
  example_msft_group_guid        = "fbe2bf47-16c8-47cf-b4a5-4b9b187c508b"
  msft_onedrive_example_drive_id = "b!-RIj2DuyvEyV1T4NlOaMHk8XkS_I8MdFlUCq1BlcjgmhRfAj3-Z8RY2VpuvV_tpd"
  msft_onedrive_example_item_id  = "01BYE5RZ6QN3ZWBTUFOFD3GSPGOHDJD36K"
}
```
(`example_msft_user_guid` — used for the `/v1.0/users/{EXAMPLE_MSFT_USER_GUID}/drives` example call — is shared across all Microsoft 365 connectors; see the [Microsoft 365 README](../README.md).)

## Example Rules

- [Example Rules](msft-onedrive.yaml)
- [Example Rules: no App IDs](msft-onedrive_no-app-ids.yaml)
