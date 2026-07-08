# Google Drive&trade;

**Connector ID:** `gdrive`

**Availability:** GA

Please review the [Google Workspace&trade; README](../README.md) for general information applicable to
all Google Workspace connectors.

## Required OAuth Scopes
- `drive.metadata.readonly`

## Required GCP APIs

Enable the following API in the GCP project where you provision the OAuth client:

- `drive.googleapis.com` (Google Drive API)

## Domain-wide Delegation Scopes

Paste the following comma-separated list into the **Scopes** field when granting Domain-wide Delegation in the Google Workspace Admin console:

```
https://www.googleapis.com/auth/drive.metadata.readonly
```

## Examples

- [Example Rules](gdrive.yaml)
- Example Data:
  - [v2-original/files.json](example-api-responses/v2-original/files.json) |
        [v2-sanitized/files.json](example-api-responses/v2-sanitized/files.json)
  - [v3-original/files.json](example-api-responses/v3-original/files.json) |
      [v3-sanitized/files.json](example-api-responses/v3-sanitized/files.json)

See more examples in the `docs/sources/google-workspace/gdrive/example-api-responses` folder
of the [Psoxy repository](https://github.com/Worklytics/psoxy).


---
Google Workspace&trade; and related marks are trademarks of Google LLC.
Worklytics&trade; is a trademark of Worklytics, Corp.
