# Slack Discovery API

## Examples

* [Example Rules](discovery.yaml)
* Example Data : [original](example-api-responses/original) | [sanitized](example-api-responses/sanitized)

## Steps to Connect

For enabling Slack Discovery with the Psoxy you must first set up an app on your Slack Enterprise
instance.

1. Go to https://api.slack.com/apps and create an app.
    - Select "From scratch", choose a name (for example "Worklytics connector") and a development workspace

![](./img/slack-step-1.png)

![](./img/slack-step-2.png)

2. Take note of your App ID (listed in "App Credentials"), contact your Slack representative and ask
   them to enable `discovery:read` scope for that App ID.
   If they also enable `discovery:write` then delete it for safety, the app just needs read access.

![](./img/slack-step-3.png)

The next step depends on your installation approach you might need to change slightly

#### Org wide install

Use this step if you want to install in the whole org, across multiple workspaces.

1. Add a bot scope (not really used, but Slack doesn't allow org-wide installations without a bot scope).
   The app won't use it at all. Just add for example the `users:read` scope, read-only.

![](./img/slack-step-scopes.png)

2. Under "Settings > Manage Distribution > Enable Org-Wide App installation",
   click on "Opt into Org Level Apps", agree and continue. This allows to distribute the app internally
   on your organization, to be clear it has nothing to do with public distribution or Slack app directory.

![](./img/slack-step-distribution.png)

3. Generate the following URL replacing the placeholder for *YOUR_CLIENT_ID* and save it for
   later:

   `https://api.slack.com/api/oauth.v2.access?client_id=YOUR_CLIENT_ID`

4. Go to "OAuth & Permissions" and add the previous URL as "Redirect URLs"

![](./img/slack-step-redirect-urls.png)

5. Go to "Settings > Install App", and choose "Install to Organization". A Slack admin should grant
   the app the permissions and the app will be installed.

![](./img/slack-step-install-org.png)

6. Copy the "User OAuth Token" (also listed under "OAuth & Permissions") and store as
   `PSOXY_SLACK_DISCOVERY_API_ACCESS_TOKEN` in the psoxy's Secret
   Manager. Otherwise, share the token with the AWS/GCP administrator completing the implementation.

#### Workspace install

Use this steps if you intend to install in just one workspace within your org.

1. Go to "Settings > Install App", click on "Install into *workspace*"
2. Copy the "User OAuth Token" (also listed under "OAuth & Permissions") and store as
   `PSOXY_SLACK_DISCOVERY_API_ACCESS_TOKEN` in the psoxy's Secret
   Manager. Otherwise, share the token with the AWS/GCP administrator completing the implementation.

## Bulk Data Flow

***beta*** As an alternative to connecting Worklytics to the Slack Discovery API via the proxy, it
is possible to use the bulk-mode of the proxy to sanitize an export of Slack Discovery data and
ingest the resulting sanitized data to Worklytics. Example data of this is giving in the
[`example-bulk/`](example-bulk/) folder.

This data can be processing using custom multi-file type rules in the proxy, of which
[`discovery-bulk.yaml`](discovery-bulk.yaml) is an example.





