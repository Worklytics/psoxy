# Slack Discovery API

## Examples

  * [Example Rules](example-rules/slack/discovery.yaml)
  * Example Data : [original](api-response-examples/slack) | [sanitized](api-response-examples/slack/sanitized)

## Steps to Connect

For enabling Slack Discovery with the Psoxy you must first setup an app on your Slack Enterprise
instance.

1. Go to https://api.slack.com/apps and create an app, select name a development workspace

2. Take note of your App ID and contact your Slack rep and ask them to enable `discovery:read` scope for the app.
   If they also enable `discovery:write` then delete it for safety, the app just needs read access.

3. Generate the following URL replacing the placeholders for *YOUR_CLIENT_ID* and *YOUR_APP_SECRET* and save it for
   later

`https://api.slack.com/api/oauth.v2.access?client_id=YOUR_CLIENT_ID&client_secret=YOUR_APP_SECRET`

4. Go to OAuth & Permissions > Redirect URLs and add the previous URL there

The next step depends on your installation approach you might need to change slightly

#### Org wide install

Use this step if you want to install in the whole org, across multiple workspaces.

1. Add a bot scope (not really used, but Slack doesn't allow org-wide without a bot scope requested).
   Just add `users:read`, something that is read-only and we already have access through discovery.
2. Go to *Org Level Apps* and Opt-in to the program
3. Go to Settings > Install App
4. Install into *organization*
5. Copy the User OAuth Token
6. If you are implementing the Proxy, then add the access token as `PSOXY_ACCESS_TOKEN_psoxy-slack-discovery-api` secret
   value in the Secret Manager for the Proxy
   Otherwise, share the token with the AWS/GCP administrator completing the implementation.

#### Workspace install

Use this steps if you intend to install in just one workspace within your org.

1. Go to Settings > Install App
2. Install into *workspace*
3. Copy the User OAuth Token and store it in the secret manager (or share with the administrator completing the
   implementation)
4. Add the access token as `PSOXY_ACCESS_TOKEN_psoxy-slack-discovery-api` secret value in the GCP Project's Secret
   Manager
