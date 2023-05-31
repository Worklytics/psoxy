# Jira Cloud

NOTE: This is for the Cloud-hosted version of Jira; for the self-hosted version, see [Jira Server](jira-server.md).

NOTE: derived from [worklytics-connector-specs](../../infra/modules/worklytics-connector-specs/main.tf); refer to that for definitive information.

Jira OAuth 2.0 (3LO) through Psoxy requires a Jira Cloud account with following classical scopes:

  - `read:jira-user`: for getting generic user information
  - `read:jira-work`: for getting information about issues, comments, etc

And following granular scopes:
  - `read:account`: for getting user emails
  - `read:group:jira`: for retrieving group members
  - `read:avatar:jira`: for retrieving group members

Setup:

1. Go to https://developer.atlassian.com/console/myapps/ and click on "Create"
2. Then go `Authorize` and `Add` it, adding `http://localhost` as callback URI. It can be any URL meanwhile it matches the settings.
3. Now go on `Permissions` and click on `Add` for Jira. Once added, click on `Configure`.
   Add following scopes as part of `Classic Scopes`:
     - `read:jira-user`
     - `read:jira-work`
   And these from `Granular Scopes`:
     - `read:group:jira`
     - `read:avatar:jira`
     - `read:user:jira`
   Then repeat the same but for `User Identity API`, adding the following scope:
     - `read:account`

4. Once Configured, go to `Settings` and prepare to copy the `Client Id` and `Secret`. As we will need to create a `REFRESH_TOKEN` we will need to exchange
   the authentication code to retrieve it. Please replace the *Client Id* field in this URL and paste it on the browser :

   `https://auth.atlassian.com/authorize?audience=api.atlassian.com&client_id=<CLIENT ID>&scope=offline_access%20read:group:jira%20read:avatar:jira%20read:user:jira%20read:account%20read:jira-user%20read:jira-work&redirect_uri=http://localhost&state=YOUR_USER_BOUND_VALUE&response_type=code&prompt=consent`

Choose a site in your Jira workspace to allow access for this application and click on `Accept`.

As the callback is not existing, you will see an error. But in the URL of your browser you will see something like this as URL:

`http://localhost/?state=YOUR_USER_BOUND_VALUE&code=eyJhbGc...`

The content of the `code` parameter is the `authentication code` required for next step.

**NOTE** This `Authorization Code` if for a one single use; if expired or used you will need to get it again pasting the URL in the browser.
5. Now, replace the values in following URL and run it from command line in your terminal. Replace `YOUR_AUTHENTICATION_CODE`, `YOUR_CLIENT_ID` and `YOUR_CLIENT_SECRET` in the placeholders:

`curl --request POST --url 'https://auth.atlassian.com/oauth/token' --header 'Content-Type: application/json' --data '{"grant_type": "authorization_code","client_id": "YOUR_CLIENT_ID","client_secret": "YOUR_CLIENT_SECRET", "code": "YOUR_AUTHENTICATION_CODE", "redirect_uri": "http://localhost"}'`

6. After running that command, if successful you will see a [JSON response](https://developer.atlassian.com/cloud/jira/platform/oauth-2-3lo-apps/#2--exchange-authorization-code-for-access-token) like this:

```json
{
    "access_token": "some short live access token",
    "expires_in": 3600,
    "token_type": "Bearer",
    "refresh_token": "some long live token we are going to use",
    "scope": "read:jira-work offline_access read:jira-user"
}
```
7. You will need to provide `cloudId` parameter of your Jira instance. To retrieve it, please run the following command replacing adding the
   `ACCESS_TOKEN` obtained in the previous step:

`curl --header 'Authorization: Bearer <ACCESS_TOKEN>' --url 'https://api.atlassian.com/oauth/token/accessible-resources'`

And its response will be something like:

```json
[
  {
  "id":"SOME UUID",
  "url":"https://your-site.atlassian.net",
  "name":"your-site-name",
  "scopes":["read:jira-user","read:jira-work"],
  "avatarUrl":"https://site-admin-avatar-cdn.prod.public.atl-paas.net/avatars/240/rocket.png"
  }
]
```

Use that id as `jira_cloud_id` parameter to include as part of Terraform deployment. That will target your instance for REST API requests.
8. Finally set following variables in AWS System Manager parameters store / GCP Cloud Secrets (if default implementation):
- `PSOXY_JIRA_CLOUD_ACCESS_TOKEN` secret variable with value of `access_token` received in previous response
- `PSOXY_JIRA_CLOUD_REFRESH_TOKEN` secret variable with value of `refresh_token` received in previous response
- `PSOXY_JIRA_CLOUD_CLIENT_ID` with `Client Id` value.
- `PSOXY_JIRA_CLOUD_CLIENT_SECRET` with `Client Secret` value.
