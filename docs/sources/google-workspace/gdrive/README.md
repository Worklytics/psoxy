# Google Drive&trade;

**Connector ID:** `gdrive`

**Availability:** GA

Please review the [Google Workspace&trade; README](../README.md) for general information applicable to all Google Workspace connectors.

## Required OAuth Scopes

- `drive.readonly` (as of v0.7.0; previously `drive.metadata.readonly`)

For Domain-wide Delegation in the Google Workspace Admin console, paste the following comma-separated list into the **Scopes** field:

```
https://www.googleapis.com/auth/drive.readonly
```

## Scope change in v0.7.0 (action required)

v0.7.0 migrates this connector from `drive.metadata.readonly` to `drive.readonly`. Existing deployments must update **both** the proxy configuration and the Domain-wide Delegation grant. A proxy release cannot change the Admin console grant on your behalf.

### Why

Google's Drive API `revisions.list` endpoint (`/drive/v2/files/{fileId}/revisions` and the v3 equivalent) returns incomplete — often empty — revision lists for Google Docs, Sheets, and Slides when the request is authorized with a metadata-only scope. The API responds `200 OK` with whatever it compiled before an internal timeout, so the result looks like a file with no history rather than an authorization error.

Binary uploads (PDFs and similar) are unaffected. File, permission, and other Drive metadata continue to collect under the old scope; the gap is revision history for Google Editors formats.

Google has confirmed this is **intended, permanent** behavior of the legacy collaborative DocumentStoreService backend under metadata-only scopes. Compiling Editors revision histories under those scopes triggers high-latency lookups; the API cuts those lookups off after a strict internal ~2-second timeout to avoid 504/500 failures. Google's engineering teams have evaluated a backend redesign for metadata-only scopes and confirmed it is not planned. The officially recommended path for complete revision histories is `https://www.googleapis.com/auth/drive.readonly`.

Google documents the limitation on [`revisions.list` (v2)](https://developers.google.com/workspace/drive/api/reference/rest/v2/revisions/list) and [v3](https://developers.google.com/workspace/drive/api/reference/rest/v3/revisions/list):

> Important: The list of revisions returned by this method might be incomplete for files with a large revision history, including frequently edited Google Docs, Sheets, and Slides. Older revisions might be omitted from the response, meaning the first revision returned may not be the oldest existing revision.

### Upgrade steps

1. **Proxy administrator** — apply this release so the gdrive instance's `OAUTH_SCOPES` environment variable becomes `https://www.googleapis.com/auth/drive.readonly` (Terraform modules pick this up from the connector spec). If you manage scopes outside Terraform, change `OAUTH_SCOPES` yourself.
2. **Google Workspace admin** — in Admin console → Security → Access and Data Control → API Controls → Domain-wide Delegation, find the gdrive proxy service account and change its authorized scope from `https://www.googleapis.com/auth/drive.metadata.readonly` to `https://www.googleapis.com/auth/drive.readonly`.

Both steps are required. After they are done, revision history can be backfilled; nothing has been permanently lost in Google Drive.

### What this scope does *not* change

`drive.readonly` can read file content, but this connector still requests only file, revision, and permission **metadata**. The proxy continues to redact file titles, descriptions, original filenames, and user display names, and to pseudonymize email addresses, before data leaves your environment. Rules are in [`gdrive.yaml`](gdrive.yaml) and can be reviewed in this repository.

## Required GCP APIs

Enable the following API in the GCP project where you provision the OAuth client:

- `drive.googleapis.com` (Google Drive API)

## Examples

- [Example Rules](gdrive.yaml)
- Example Data:
  - [v2-original/files.json](example-api-responses/v2-original/files.json) |
        [v2-sanitized/files.json](example-api-responses/v2-sanitized/files.json)
  - [v3-original/files.json](example-api-responses/v3-original/files.json) |
      [v3-sanitized/files.json](example-api-responses/v3-sanitized/files.json)

See more examples in the `docs/sources/google-workspace/gdrive/example-api-responses` folder of the [Psoxy repository](https://github.com/Worklytics/psoxy).


---
Google Workspace&trade; and related marks are trademarks of Google LLC.
Worklytics&trade; is a trademark of Worklytics, Corp.
