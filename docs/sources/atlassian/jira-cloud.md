# Jira Cloud **alpha**

NOTE: This is for the Cloud-hosted version of Jira; for the self-hosted version, see [Jira Server](jira-server.md).

NOTE: These instructions are derived from [worklytics-connector-specs](../../../infra/modules/worklytics-connector-specs/main.tf); refer to that for definitive information.

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

### Manual configuration
1. Go to the [Atlassian Developer Console](https://developer.atlassian.com/console/myapps/) and
   click on "Create" (OAuth 2.0 integration).

2. Now navigate to "Permissions" and click on "Add" for Jira. Once added, click on "Configure".
   Add the following scopes as part of "Classic Scopes":
   - `read:jira-user`
   - `read:jira-work`

   And these from "Granular Scopes":
   - `read:group:jira`
   - `read:avatar:jira`
   - `read:user:jira`

   Then repeat the same but for "User Identity API", adding the following scope:
   - `read:account`

3. Go to the "Authorization" section and add an OAuth 2.0 (3LO) authorization type: click on "Add"
   and you will be prompted to provide a "Callback URI". At this point, you could add
   `http://localhost` as value and follow the rest of steps on this guide, or you could use our
   [Psoxy OAuth setup tool](#worklytics-psoxy-oauth-setup-tool).

4. Once Configured, go to "Settings" and copy the "Client ID" and "Secret". You will use these to
   obtain an OAuth `refresh_token`.

5. Build an OAuth authorization endpoint URL by copying the value for "Client Id" obtained in the
   previous step into the URL below. Then open the result in a web browser:

   `https://auth.atlassian.com/authorize?audience=api.atlassian.com&client_id=<CLIENT ID>&scope=offline_access%20read:group:jira%20read:avatar:jira%20read:user:jira%20read:account%20read:jira-user%20read:jira-work&redirect_uri=http://localhost&state=YOUR_USER_BOUND_VALUE&response_type=code&prompt=consent`

6. Choose a site in your Jira workspace to allow access for this application and click "Accept".
   As the callback does not exist, you will see an error. But in the URL of your browser you will
   see something like this as URL:

   `http://localhost/?state=YOUR_USER_BOUND_VALUE&code=eyJhbGc...`

   Copy the value of the `code` parameter from that URI. It is the "authorization code" required
   for next step.

   **NOTE** This "Authorization Code" is single-use; if it expires or is used, you will need to
   obtain a new code by  again pasting the authorization URL in the browser.

7. Now, replace the values in following URL and run it from command line in your terminal. Replace
   `YOUR_AUTHENTICATION_CODE`, `YOUR_CLIENT_ID` and `YOUR_CLIENT_SECRET` in the placeholders:

   `curl --request POST --url 'https://auth.atlassian.com/oauth/token' --header 'Content-Type: application/json' --data '{"grant_type": "authorization_code","client_id": "YOUR_CLIENT_ID","client_secret": "YOUR_CLIENT_SECRET", "code": "YOUR_AUTHENTICATION_CODE", "redirect_uri": "http://localhost"}'`

8. After running that command, if successful you will see a
   [JSON response](https://developer.atlassian.com/cloud/jira/platform/oauth-2-3lo-apps/#2--exchange-authorization-code-for-access-token) like this:

```json
{
  "access_token": "some short live access token",
  "expires_in": 3600,
  "token_type": "Bearer",
  "refresh_token": "some long live token we are going to use",
  "scope": "read:jira-work offline_access read:jira-user"
}
```

9. Set the following variables in AWS System Manager parameters store / GCP Cloud Secrets (if default implementation):
   - `PSOXY_JIRA_CLOUD_ACCESS_TOKEN` secret variable with value of `access_token` received in previous response
   - `PSOXY_JIRA_CLOUD_REFRESH_TOKEN` secret variable with value of `refresh_token` received in previous response
   - `PSOXY_JIRA_CLOUD_CLIENT_ID` with `Client Id` value.
   - `PSOXY_JIRA_CLOUD_CLIENT_SECRET` with `Client Secret` value.

 10. Optional, obtain the "Cloud ID" of your Jira instance. Use the following command, with the
    `access_token` obtained in the previous step in place of `<ACCESS_TOKEN>` below:

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

Add the `id` value from that JSON response as the value of the `jira_cloud_id` variable in the
`terraform.tfvars` file of your Terraform configuration. This will generate all the test URLs with
a proper value.

### Worklytics Psoxy OAuth setup tool

Assuming you've created a Jira Cloud OAuth 2.0 (3LO) integration as described above, from the
third step of the "manual configuration", you can use our
[Psoxy OAuth setup tool](https://github.com/Worklytics/psoxy-oauth-setup-tool) to obtain the tokens.

Once you've installed and run the tool, you will get a Callback URI like this:
`http://localhost:9000/psoxy-setup-callback` (instead of just `http://localhost`) that you can
use in the "Authorization" section of the Developer Console. The tool is interactive, and you
will be prompted to confirm that you've registered the Callback URI before continuing.

Then, you will be prompted to enter the "Client ID" and "Secret" from the Developer Console, and
the tool will open a web browser to complete the authentication and authorization flows. After that,
it will print the necessary values to complete the configuration (as in step 9 above).
