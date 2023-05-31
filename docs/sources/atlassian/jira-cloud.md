# Jira Cloud

NOTE: This is for the Cloud-hosted version of Jira; for the self-hosted version, see [Jira Server](jira-server.md).

NOTE: These instructions are derived from [worklytics-connector-specs](../../infra/modules/worklytics-connector-specs/main.tf); refer to that for definitive information.

## Prerequisites

Jira Cloud through Psoxy uses Jira OAuth 2.0 (3LO), which a Jira Cloud (user) account with following
classical scopes:

  - `read:jira-user`: for getting generic user information
  - `read:jira-work`: for getting information about issues, comments, etc

And following granular scopes:
  - `read:account`: for getting user emails
  - `read:group:jira`: for retrieving group members
  - `read:avatar:jira`: for retrieving group members

## Setup Instructions

  1. Go to https://developer.atlassian.com/console/myapps/ and click on "Create"

  2. Then click "Authorize" and "Add", adding `http://localhost` as callback URI. It can be any URL
     that matches the settings.

  3. Now navigate to "Permissions" and click on "Add" for Jira. Once added, click on "Configure".
     Add following scopes as part of "Classic Scopes":
       - `read:jira-user`
       - `read:jira-work`
     And these from "Granular Scopes":
       - `read:group:jira`
       - `read:avatar:jira`
       - `read:user:jira`
     Then repeat the same but for "User Identity API", adding the following scope:
       - `read:account`

  4. Once Configured, go to "Settings" and copy the "Client Id" and "Secret". You will use these to
     obtain an OAuth `refresh_token`.

  5. Build an OAuth authorization endpoint URL by copying the value for "Client Id" obtained in the
    previous step into the URL below. Then open the result in a web browser:

   `https://auth.atlassian.com/authorize?audience=api.atlassian.com&client_id=<CLIENT ID>&scope=offline_access%20read:group:jira%20read:avatar:jira%20read:user:jira%20read:account%20read:jira-user%20read:jira-work&redirect_uri=http://localhost&state=YOUR_USER_BOUND_VALUE&response_type=code&prompt=consent`

  6. Choose a site in your Jira workspace to allow access for this application and click "Accept".

    As the callback does not exist, you will see an error. But in the URL of your browser you will see
    something like this as tURL:

    `http://localhost/?state=YOUR_USER_BOUND_VALUE&code=eyJhbGc...`

    Copy the value of the `code` parameter from that URI. It is the "authorization code" required for next step.

    **NOTE** This "Authorization Code" is single-use; if it expires or is used, you will need to obtain
    a new code by  again pasting the authorization URL in the browser.

    7. Now, replace the values in following URL and run it from command line in your terminal. Replace `YOUR_AUTHENTICATION_CODE`, `YOUR_CLIENT_ID` and `YOUR_CLIENT_SECRET` in the placeholders:

    `curl --request POST --url 'https://auth.atlassian.com/oauth/token' --header 'Content-Type: application/json' --data '{"grant_type": "authorization_code","client_id": "YOUR_CLIENT_ID","client_secret": "YOUR_CLIENT_SECRET", "code": "YOUR_AUTHENTICATION_CODE", "redirect_uri": "http://localhost"}'`

    8. After running that command, if successful you will see a [JSON response](https://developer.atlassian.com/cloud/jira/platform/oauth-2-3lo-apps/#2--exchange-authorization-code-for-access-token) like this:

```json
{
  "access_token": "some short live access token",
  "expires_in": 3600,
  "token_type": "Bearer",
  "refresh_token": "some long live token we are going to use",
  "scope": "read:jira-work offline_access read:jira-user"
}
```

 9. You will need to provide `cloudId` parameter of your Jira instance. To retrieve it, please run the
     following command, using the `access_token` obtained in the previous step in place of
     `<ACCESS_TOKEN>` below:

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

  Use that id as `jira_cloud_id` parameter to include as part of Terraform deployment. That will
  target your instance for REST API requests.

 10. Set the following variables in AWS System Manager parameters store / GCP Cloud Secrets (if default implementation):
     - `PSOXY_JIRA_CLOUD_ACCESS_TOKEN` secret variable with value of `access_token` received in previous response
     - `PSOXY_JIRA_CLOUD_REFRESH_TOKEN` secret variable with value of `refresh_token` received in previous response
     - `PSOXY_JIRA_CLOUD_CLIENT_ID` with `Client Id` value.
     - `PSOXY_JIRA_CLOUD_CLIENT_SECRET` with `Client Secret` value.
