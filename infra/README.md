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

3. Generate the following URL replacing the placeholders for *YOUR_CLIENT_ID* and *YOUR_APP_SECRET* and save it for
   later

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
6. If you are implementing the Proxy, then add the access token as `PSOXY_ACCESS_TOKEN_psoxy-slack-discovery-api` secret
   value in the Secret Manager for the Proxy
   Otherwise, share the token with the AWS/GCP administrator completing the implementation.

### Workspace install

Use this steps if you intend to install in just one workspace within your org.

1. Go to Settings > Install App
2. Install into *workspace*
3. Copy the User OAuth Token and store it in the secret manager (or share with the administrator completing the
   implementation)
4. Add the access token as `PSOXY_ACCESS_TOKEN_psoxy-slack-discovery-api` secret value in the GCP Project's Secret
   Manager

## Zoom Setup

Zoom connector through Psoxy requires a custom managed app on the Zoom Marketplace (in development
mode, no need to publish).

1. Go to https://marketplace.zoom.us/develop/create and create an app of type "Server to Server OAuth"
2. After creation, it will show the App Credentials. Share them with the AWS/GCP administrator, the
   following secret values must be filled in the Secret Manager for the Proxy with the appropriate values:

    - `PSOXY_ZOOM_CLIENT_ID`
    - `PSOXY_ZOOM_ACCOUNT_ID`
    - `PSOXY_ZOOM_CLIENT_SECRET`
    - Note: Anytime the *client secret* is regenerated it needs to be updated in the Proxy too.

3. Fill the information section

4. Fill the scopes section, enabling the following:

   - Users / View all user information / `user:read:admin`
     - To be able to gather information about the zoom users
   - Meetings / View all user meetings / `meeting:read:admin`
     - Allows us to list all user meeting
   - Report / View report data / `report:read:admin`
     - Last 6 months view for user meetings

5. Activate the app

## Dropbox Setup

Dropbox connector through Psoxy requires a Dropbox Application created in Dropbox Console. The application
does not require to be public, and it needs to have the following scopes to support
all the operations for the connector:

- files.metadata.read: for file listing and revision
- members.read: member listing
- events.read: event listing
- groups.read: group listing

1. Go to https://www.dropbox.com/apps and Build an App
2. Then go https://www.dropbox.com/developers to enter in `App Console` to configure your app
3. Now you are in the app, go to `Permissions` and mark all the scopes described before. NOTE: Probably the UI will mark
   you more required permissions automatically (like *account_info_read*.) Just mark the ones
   described here and the UI will ask you to include any other required.
4. On settings, you could access to `App key` and `App secret`. You can create an access token here, but with limited
   expiration. We need to create a long-lived token, so edit the following URL with your `App key` and paste it into the
   browser:

   `https://www.dropbox.com/oauth2/authorize?client_id=<APP_KEY>&token_access_type=offline&response_type=code`

   That will return an `Authorization Code` that you have to paste.
   **NOTE** This `Authorization Code` if for a one single use; if expired or used you will need to get it again pasting
   the
   URL in the browser.
5. Now, replace the values in following URL and run it from command line in your terminal. Replace `Authorization Code`
   , `App key`
   and `App secret` in the placeholders:

   `curl https://api.dropbox.com/oauth2/token -d code=<AUTHORIZATION_CODE> -d grant_type=authorization_code -u <APP_KEY>:<APP_SECRET>`
6. After running that command, if successful you will see
   a [JSON response](https://www.dropbox.com/developers/documentation/http/documentation#oauth2-authorize) like this:

```json
{
  "access_token": "some short live access token",
  "token_type": "bearer",
  "expires_in": 14399,
  "refresh_token": "some long live token we are going to use",
  "scope": "account_info.read events.read files.metadata.read groups.read members.read team_data.governance.read team_data.governance.write team_data.member",
  "uid": "",
  "team_id": "some team id"
}
```

7. Finally set following variables in AWS System Manager parameters store / GCP Cloud Secrets (if default
   implementation):

- `PSOXY_dropbox_business_REFRESH_TOKEN` secret variable with value of `refresh_token` received in previous response
- `PSOXY_dropbox_business_CLIENT_ID` with `App key` value.
- `PSOXY_dropbox_business_CLIENT_SECRET` with `App secret` value.
