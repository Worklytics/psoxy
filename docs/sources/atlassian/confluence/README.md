# Confluence Cloud

NOTE: These instructions are derived from [worklytics-connector-specs](../../../infra/modules/worklytics-connector-specs/main.tf); refer to that for definitive information.

## Examples

- [Example Rules](confluence.yaml)
- Example Data : [original/content_search.json](example-api-responses/original/content_search.json) |
  [sanitized/content_search.json](example-api-responses/sanitized/content_search.json)

See more examples in the `docs/sources/atlassian/confluence/example-api-responses` folder
of the [Psoxy repository](https://github.com/Worklytics/psoxy).

## Prerequisites
Confluence OAuth 2.0 (3LO) through Psoxy requires a Confluence Cloud account with following granular scopes:

Add following scopes as part of \"Granular Scopes\", first clicking on \`Edit Scopes\` and then selecting them:
- `read:blogpost:confluence`: for getting [blogposts and their versions](https://developer.atlassian.com/cloud/confluence/rest/v2/api-group-version/#api-blogposts-id-versions-get)
- `read:comment:confluence`: for getting [footer-comments](https://developer.atlassian.com/cloud/confluence/rest/v2/api-group-version/#api-footer-comments-id-versions-get) and [inline-comments](https://developer.atlassian.com/cloud/confluence/rest/v2/api-group-version/#api-inline-comments-id-versions-get) and their versions
- `read:group:confluence`: for getting [groups](https://developer.atlassian.com/cloud/confluence/rest/v1/api-group-group/#api-wiki-rest-api-group-get)
- `read:user:confluence`: for getting [users from groups](https://developer.atlassian.com/cloud/confluence/rest/v1/api-group-group/#api-wiki-rest-api-group-groupid-membersbygroupid-get)
- `read:space:confluence`: for getting [spaces](https://developer.atlassian.com/cloud/confluence/rest/v2/api-group-space/#api-spaces-get)
- `read:attachment:confluence`: for getting [attachments and their versions](https://developer.atlassian.com/cloud/confluence/rest/v2/api-group-version/#api-attachments-id-versions-get)
- `read:page:confluence`: for getting [pages and their versions](https://developer.atlassian.com/cloud/confluence/rest/v2/api-group-version/#api-pages-id-versions-get)
- `read:task:confluence`: for getting [tasks](https://developer.atlassian.com/cloud/confluence/rest/v2/api-group-task/#api-tasks-get)
- `read:content-details:confluence`: for using [content search endpoint](https://developer.atlassian.com/cloud/confluence/rest/v1/api-group-search/#api-wiki-rest-api-search-get)

## Setup Instructions

### App configuration
1. Go to the [Atlassian Developer Console](https://developer.atlassian.com/console/myapps/) and
   click on "Create" (OAuth 2.0 integration).
2. Now navigate to "Permissions" and click on "Add" for Confluence. Once added, click on "Configure".
   Add following scopes as part of \"Granular Scopes\", first clicking on \`Edit Scopes\` and then selecting them:
    - `read:blogpost:confluence`
    - `read:comment:confluence`
    - `read:group:confluence`
    - `read:space:confluence`
    - `read:attachment:confluence`
    - `read:page:confluence`
    - `read:user:confluence`
    - `read:task:confluence`
    - `read:content-details:confluence`
    - `read:content:confluence`

3. Go to the "Authorization" section and add an OAuth 2.0 (3LO) authorization type: click on "Add"
   and you will be prompted to provide a "Callback URI". At this point, you could add
   `http://localhost` as value and follow the [Manual steps](#manual-steps), or you could
   use our [Psoxy OAuth setup tool](#worklytics-psoxy-oauth-setup-tool) (see details below).

### Worklytics OAuth setup tool
Assuming you've created a Confluence Cloud OAuth 2.0 (3LO) integration as described above, from the
use our [Psoxy OAuth setup tool](https://github.com/Worklytics/psoxy-oauth-setup-tool) to obtain
the necessary OAuth tokens and your Confluence Cloud ID.
Once you've installed and run the tool, you will get a Callback URI like this:
`http://localhost:9000/psoxy-setup-callback` (instead of just `http://localhost`) that you can
use in the "Authorization" section of the Developer Console. The tool is interactive, and you
will be prompted to confirm that you've registered the Callback URI before continuing.
Then, you will be prompted to enter the "Client ID" and "Secret" from the Developer Console, and
the tool will open a web browser to perform the authentication and authorization flows. After that,
it will print the all the values to complete the configuration:
- OAuth tokens, Client ID and Secret to be stored in AWS System Manager parameters store / GCP
  Cloud Secrets (if default implementation).

### Manual steps
1. Assuming you've created a Confluence Cloud OAuth 2.0 (3LO) integration as described above, go to
   "Settings" and copy the "Client ID" and "Secret". You will use these to obtain an OAuth
   `refresh_token`.
2. Build an OAuth authorization endpoint URL by copying the value for "Client Id" obtained in the
   previous step into the URL below. Then open the result in a web browser:
   `https://auth.atlassian.com/authorize?audience=api.atlassian.com&client_id=${CLIENT_ID}&scope=offline_access%20read:task:confluence%20read%3Ablogpost%3Aconfluence%20read%3Acomment%3Aconfluence%20read%3Agroup%3Aconfluence%20read%3Aspace%3Aconfluence%20read%3Aattachment%3Aconfluence%20read%3Apage%3Aconfluence%20read%3Auser%3Aconfluence%20read%3Atask%3Aconfluence%20read%3Acontent-details%3Aconfluence%20read%3Acontent%3Aconfluence&redirect_uri=http%3A%2F%2Flocalhost&state=YOUR_USER_BOUND_VALUE&response_type=code&prompt=consent`
3. Choose a site in your Confluence workspace to allow access for this application and click "Accept".
   As the callback does not exist, you will see an error. But in the URL of your browser you will
   see something like this as URL:
   `http://localhost/?state=YOUR_USER_BOUND_VALUE&code=eyJhbGc...`
   Copy the value of the `code` parameter from that URI. It is the "authorization code" required
   for next step.
   **NOTE** This "Authorization Code" is single-use; if it expires or is used, you will need to
   obtain a new code by  again pasting the authorization URL in the browser.
4. Now, replace the values in following URL and run it from command line in your terminal. Replace
   `YOUR_AUTHENTICATION_CODE`, `YOUR_CLIENT_ID` and `YOUR_CLIENT_SECRET` in the placeholders:
```shell
curl --request POST --url 'https://auth.atlassian.com/oauth/token' --header 'Content-Type: application/json' --data '{"grant_type": "authorization_code","client_id": "YOUR_CLIENT_ID","client_secret": "YOUR_CLIENT_SECRET", "code": "YOUR_AUTHENTICATION_CODE", "redirect_uri": "http://localhost"}'`
```
5. After running that command, if successful you will see a
   [JSON response](https://developer.atlassian.com/cloud/confluence/platform/oauth-2-3lo-apps/#2--exchange-authorization-code-for-access-token) like this:
   ```json
   {
    "access_token": "some short live access token",
    "expires_in": 3600,
    "token_type": "Bearer",
    "refresh_token": "some long live token we are going to use",
    "scopes": [
            "read:attachment:confluence",
            "read:blogpost:confluence",
            "read:comment:confluence",
            "read:content-details:confluence",
            "read:group:confluence",
            "read:page:confluence",
            "read:space:confluence",
            "read:user:confluence"
        ],
   }
   ```
       NOTE: As per September 2025, scopes don't show `read:task:confluence` in the response.
6. Set the following variables in AWS System Manager parameters store / GCP Cloud Secrets (if default implementation):
    - `PSOXY_CONFLUENCE_CLOUD_REFRESH_TOKEN` secret variable with value of `refresh_token` received in previous response
    - `PSOXY_CONFLUENCE_CLOUD_CLIENT_ID` with `Client Id` value.
    - `PSOXY_CONFLUENCE_CLOUD_CLIENT_SECRET` with `Client Secret` value.
7. Optional, obtain the "Cloud ID" of your Jira instance. Use the following command, with the
   `access_token` obtained in the previous step in place of `<ACCESS_TOKEN>` below:
   `curl --header 'Authorization: Bearer <ACCESS_TOKEN>' --url 'https://api.atlassian.com/oauth/token/accessible-resources'`
   And its response will be something like:
   ```json
   [
     {
       "id":"SOME UUID",
       "url":"https://your-site.atlassian.net",
       "name":"your-site-name",
        "scopes": [
            "read:attachment:confluence",
            "read:blogpost:confluence",
            "read:comment:confluence",
            "read:content-details:confluence",
            "read:content:confluence",
            "read:group:confluence",
            "read:page:confluence",
            "read:space:confluence",
            "read:user:confluence"
        ],
       "avatarUrl":"https://site-admin-avatar-cdn.prod.public.atl-paas.net/avatars/240/rocket.png"
     }
   ]
   ```
Add the `id` value from that JSON response as the value of the `confluence_example_cloud_id` variable in the
`terraform.tfvars` file of your Terraform configuration. This will generate all the test URLs with
a proper value.

NOTE: A "token family" includes the initial access/refresh tokens generated above as well as all subsequent access/refresh tokens that Jira returns to any future token refresh requests. By default, Jira enforces a maximum lifetime of 1 year for each **token family**. So you MUST repeat steps 5-9 at least annually or your proxy instance will stop working when the token family expires.

### Troubleshooting

If a request is done and the following content is returned:
```
{
"code": 401,
"message": "Unauthorized; scope does not match"
}
```

It could mean:
- scope is not correctly set in the app configuration. Please check the "Prerequisites" section above.
- endpoint doesn't exist
