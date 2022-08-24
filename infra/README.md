# infra/

Contains terraform module to setup psoxy instances in various cloud providers. As terraform
modules are NOT cloud agnostic, we follow the same pattern and provide distinct module per
provider, rather than a generic solution or something that takes the provider as a variable.

Please review the `README.md` within each provider's module for pre-reqs and usage details.


## Slack Discovery Setup

For enabling Slack Discovery with the Psoxy you must first setup an app on your Slack Enterprise
instance.

1. Go to https://api.slack.com/apps and create an app, select name a development workspace

2. Take note of your App ID and contact your Slack rep and ask them to enable `discovery:read` scope for the app.
If they also enable `discovery:write` then delete it for safety, the app just needs read access.

3. Generate the following URL replacing the placeholders for *YOUR_CLIENT_ID* and *YOUR_APP_SECRET* and save it for later

`https://api.slack.com/api/oauth.v2.access?client_id=YOUR_CLIENT_ID&client_secret=YOUR_APP_SECRET`

4. Go to OAuth & Permissions > Redirect URLs and add the previous URL there

The next step depends on your installation approach you might need to change slightly

### Org wide install
Use this step if you want to install in the whole org, across multiple workspaces.

1. Add a bot scope (not really used, but Slack doesn't allow org-wide without a bot scope requested).
   Just add `users:read`, something that is read-only and we already have access through discovery.
2. Go to *Org Level Apps* and Opt-in to the program
3. Go to Settings > Install App
4. Install into *organization*
5. Copy the User OAuth Token 
6. If you are implementing the Proxy, then add the access token as `PSOXY_ACCESS_TOKEN_psoxy-slack-discovery-api` secret value in the Secret Manager for the Proxy
Otherwise, share the token with the AWS/GCP administrator completing the implementation.

### Workspace install
Use this steps if you intend to install in just one workspace within your org.

1. Go to Settings > Install App
2. Install into *workspace*
3. Copy the User OAuth Token and store it in the secret manager (or share with the administrator completing the implementation)
4. Add the access token as `PSOXY_ACCESS_TOKEN_psoxy-slack-discovery-api` secret value in the GCP Project's Secret Manager


## Zoom Setup

Zoom connector through Psoxy requires a custom managed app on the Zoom Marketplace (in development
mode, no need to publish).

1. Go to https://marketplace.zoom.us/develop/create and create an app of type JWT

2. Fill information and on App Credentials generate a token with a long expiration time, for example 00:00 01/01/2030

3. Copy the JWT Token, it will be used later when creating the Zoom cloud function.

4. Activate the app

5. Add the access token as `PSOXY_ACCESS_TOKEN_psoxy-zoom` secret value in the GCP Project's Secret Manager
