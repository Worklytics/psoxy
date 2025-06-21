### Slack via Discovery API Setup

For enabling Slack via Discovery API via the Psoxy you must first create an app on your Slack Enterprise instance. From May 31, 2025, Slack is rate limiting a subset of the methods from Discovery API for new "apps" that aren't listed in the
marketplace. If you are creating your app AFTER this point, please contact your Slack account representative
to ask that the original rate limits be applied to your app.

1. Go to https://api.slack.com/apps and create an app.
   - Select "From scratch", choose a name (for example "Worklytics connector") and a development workspace
2. Take note of your App ID (listed in "App Credentials"), contact your Slack representative and ask
   them to enable `discovery:read` scope for that App ID.
   If they also enable `discovery:write` then delete it for safety, the app just needs read access.

The next step depends on your installation approach you might need to change slightly

#### Org wide install

Use this step if you want to install in the whole org, across multiple workspaces.

1. Add a bot scope (not really used, but Slack doesn't allow org-wide installations without a bot scope).
   The app won't use it at all. Just add for example the `users:read` scope, read-only.
2. Under "Settings > Manage Distribution > Enable Org-Wide App installation",
   click on "Opt into Org Level Apps", agree and continue. This allows to distribute the app internally
   on your organization, to be clear it has nothing to do with public distribution or Slack app directory.
3. Generate the following URL replacing the placeholder for *YOUR_CLIENT_ID* and save it for
   later:
4. Go to "OAuth & Permissions" and add the previous URL as "Redirect URLs"
5. Go to "Settings > Install App", and choose "Install to Organization". A Slack admin should grant
   the app the permissions and the app will be installed.
6. Copy the "User OAuth Token" (also listed under "OAuth & Permissions") and store as
   `PSOXY_SLACK_DISCOVERY_API_ACCESS_TOKEN` in the psoxy's Secret
   Manager. Otherwise, share the token with the AWS/GCP administrator completing the implementation.

#### Workspace install

Use this steps if you intend to install in just one workspace within your org.

1. Go to "Settings > Install App", click on "Install into *workspace*"
2. Copy the "User OAuth Token" (also listed under "OAuth & Permissions") and store as
   `PSOXY_SLACK_DISCOVERY_API_ACCESS_TOKEN` in the psoxy's Secret
   Manager. Otherwise, share the token with the AWS/GCP administrator completing the implementation.
