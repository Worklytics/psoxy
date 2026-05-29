# Slack Analytics

**Connector ID:** `slack-analytics`

**Availability:** Alpha

The Slack Analytics connector provides access to Slack's Admin Analytics API. It supersedes the deprecated [`slack-discovery-api`](../slack-discovery-api/README.md) connector for organizations that have access to Slack Analytics. Compared to the Discovery API, Slack returns message metadata and activity metrics directly (without message body content), so the proxy does not need to redact message text.

## Supported Endpoints

| Method | Description |
|--------|-------------|
| [`admin.analytics.getFile`](https://docs.slack.dev/reference/methods/admin.analytics.getFile) | Daily member and public-channel analytics (gzip-compressed NDJSON). Use `type=member` for per-user activity, `type=public_channel` for channel activity, and `metadata_only=true` with `type=public_channel` for channel metadata. |
| [`admin.analytics.messages.metadata`](https://docs.slack.dev/reference/methods/admin.analytics.messages.metadata) | Message metadata for a channel (character counts, reactions, file metadata, threading) without message text. Replaces `discovery.conversations.history` for analytics use cases. |
| [`admin.analytics.messages.activity`](https://docs.slack.dev/reference/methods/admin.analytics.messages.activity) | Per-message engagement metrics (views, reactions, shares, clicks) for a channel. |

## Required OAuth Scope

The app must be granted the **`admin.analytics:read`** user token scope. This is an Enterprise Grid admin scope; the installing user must be an org owner or admin.

## Steps to Connect

For enabling Slack Analytics with the Psoxy you must first create an app on your Slack Enterprise Grid organization.

1. Go to https://api.slack.com/apps and create an app.
   - Select "From scratch", choose a name (for example "Worklytics connector") and a development workspace.

2. Under **Features → OAuth & Permissions**, add the following scope under **User Token Scopes**:
   - `admin.analytics:read`

The next step depends on whether you want org-wide or single-workspace access.

#### Org-wide install

Use this if you want to install across the whole org, spanning multiple workspaces.

1. Add a bot scope (not used by the connector, but Slack requires a bot scope for org-wide installs). For example, add the read-only `users:read` scope.
2. Under **Settings → Manage Distribution → Enable Org-Wide App installation**, click **Opt into Org Level Apps** and agree to continue. This enables internal distribution within your organization only; it does not publish the app to the Slack App Directory.
3. Generate the following URL, replacing `YOUR_CLIENT_ID` with your app's client ID, and save it for later:

   `https://api.slack.com/api/oauth.v2.access?client_id=YOUR_CLIENT_ID`

4. Go to **OAuth & Permissions** and add that URL as a **Redirect URL**.
5. Go to **Settings → Install App** and choose **Install to Organization**. A Slack admin should grant the app permissions.
6. Copy the **User OAuth Token** (also listed under **OAuth & Permissions**) and store it as `PSOXY_SLACK_ANALYTICS_ACCESS_TOKEN` in your secret manager. Otherwise, share the token with the AWS/GCP administrator completing the implementation.

#### Workspace install

Use this if you intend to install in just one workspace within your org.

1. Go to **Settings → Install App**, click **Install into _workspace_**.
2. Copy the **User OAuth Token** and store it as `PSOXY_SLACK_ANALYTICS_ACCESS_TOKEN` in your secret manager.

## Async Processing

`admin.analytics.getFile` returns large gzip-compressed NDJSON files. This connector has async processing enabled by default; send a `Prefer: respond-async` header on `getFile` requests to receive a `202 Accepted` response while the proxy fetches and sanitizes the file in the background. See [Async API Data Sanitization](../../configuration/async-api-data.md).

The `admin.analytics.messages.*` endpoints return standard JSON and are processed synchronously.

## Examples

- [Example Rules](slack-analytics.yaml)
- Example Data:
  - [member_sample.json](example-api-responses/original/member_sample.json) | [sanitized](example-api-responses/sanitized/member_sample.json)
  - [messages-metadata.json](example-api-responses/original/messages-metadata.json) | [sanitized](example-api-responses/sanitized/messages-metadata.json)
  - [messages-activity.json](example-api-responses/original/messages-activity.json) | [sanitized](example-api-responses/sanitized/messages-activity.json)

See more examples in the `docs/sources/slack/slack-analytics/example-api-responses` folder of the [Psoxy repository](https://github.com/Worklytics/psoxy).
