# Confluence Cloud

**Connector ID:** `confluence-cloud`

**Availability:** Beta

NOTE: These instructions are derived from [worklytics-connector-specs](../../../../infra/modules/worklytics-connector-specs/main.tf); refer to that for definitive information.

## Examples

- [Example Rules](confluence.yaml)
- Example Data : [original/content_search.json](example-api-responses/original/content_search.json) |
  [sanitized/content_search.json](example-api-responses/sanitized/content_search.json)

Example API call path parameters (uppercase placeholders in generated test calls):

- `{CLOUD_ID}`: Confluence Cloud id from `GET /oauth/token/accessible-resources` (`connector_settings.confluence_example_cloud_id`).
- `{GROUP_ID}`: a group id from `GET .../wiki/rest/api/group`.
- `{ATTACHMENT_ID}`: an attachment id from content search / v2 APIs (the attachments-versions rule names this path param `{pageId}`; fill it with the attachment id).
- `{BLOGPOST_ID}` / `{PAGE_ID}` / `{CUSTOM_CONTENT_ID}` / `{COMMENT_ID}`: ids of the corresponding Confluence objects whose versions you want to list. `{version}` in the rules is `v2` in the examples.

See more examples in the `docs/sources/atlassian/confluence/example-api-responses` folder
of the [Psoxy repository](https://github.com/Worklytics/psoxy).

## Prerequisites

As of this version, Confluence Cloud through Psoxy authenticates using an [Atlassian OAuth 2.0
credential for a service account](https://support.atlassian.com/user-management/docs/create-oauth-2-0-credential-for-service-accounts/)
(a "2LO"/`client_credentials` grant) rather than a user-delegated OAuth 2.0 (3LO) `refresh_token`.
This requires:

- Your Atlassian organization must be on the **centralized user management** experience
  (`admin.atlassian.com`). Org admin access is required to create the service account and its
  credential.
- A **service account** (orgs get 5 free; more available via Atlassian Guard/Enterprise) granted
  product access to the Confluence site you want to connect, with a role sufficient for the scopes
  below.

The OAuth 2.0 credential uses the following granular scopes:
- `read:blogpost:confluence`: for getting [blogposts and their versions](https://developer.atlassian.com/cloud/confluence/rest/v2/api-group-version/#api-blogposts-id-versions-get)
- `read:comment:confluence`: for getting [footer-comments](https://developer.atlassian.com/cloud/confluence/rest/v2/api-group-version/#api-footer-comments-id-versions-get) and [inline-comments](https://developer.atlassian.com/cloud/confluence/rest/v2/api-group-version/#api-inline-comments-id-versions-get) and their versions
- `read:group:confluence`: for getting [groups](https://developer.atlassian.com/cloud/confluence/rest/v1/api-group-group/#api-wiki-rest-api-group-get)
- `read:user:confluence`: for getting [users from groups](https://developer.atlassian.com/cloud/confluence/rest/v1/api-group-group/#api-wiki-rest-api-group-groupid-membersbygroupid-get)
- `read:space:confluence`: for getting [spaces](https://developer.atlassian.com/cloud/confluence/rest/v2/api-group-space/#api-spaces-get)
- `read:attachment:confluence`: for getting [attachments and their versions](https://developer.atlassian.com/cloud/confluence/rest/v2/api-group-version/#api-attachments-id-versions-get)
- `read:page:confluence`: for getting [pages and their versions](https://developer.atlassian.com/cloud/confluence/rest/v2/api-group-version/#api-pages-id-versions-get)
- `read:task:confluence`: for getting [tasks](https://developer.atlassian.com/cloud/confluence/rest/v2/api-group-task/#api-tasks-get)
- `read:content-details:confluence`: for using [content search endpoint](https://developer.atlassian.com/cloud/confluence/rest/v1/api-group-search/#api-wiki-rest-api-search-get)

> **NOTE:** This connector previously used a user-delegated OAuth 2.0 (3LO) `refresh_token` flow
> (via the [Psoxy OAuth setup tool](https://github.com/Worklytics/psoxy-oauth-setup-tool) or manual
> browser-consent steps), which required re-authorizing at least annually because Atlassian caps a
> 3LO "token family" at a 1-year max lifetime. The service-account flow below avoids that: there's
> no refresh token, and Atlassian's docs don't state an expiration for the `CLIENT_SECRET` itself.

## Setup Instructions

1. Go to [https://admin.atlassian.com](https://admin.atlassian.com), select your organization, then
   "Directory" > "Service accounts".
2. Create a new service account (or select an existing one) and grant it product access to
   Confluence on the site you want to connect, with a role sufficient for the scopes above
   (read-only access is enough for all scopes used by Psoxy).

   **NOTE** You can't change a service account's email after creation, and orgs get 5 free service
   accounts before requiring Atlassian Guard/Enterprise - plan the account name accordingly.

3. From the service account's page, go to "Settings" (or "API keys") > "OAuth 2.0 credentials" and
   create a new OAuth 2.0 credential. Select scopes for "Confluence" matching the list above.

   **NOTE** Scopes are immutable once the credential is created; to change them later, you must
   revoke the credential and create a new one.

4. Save the credential and copy the "Client ID" and "Secret" - the secret is **shown only once**.

### Recommended: use the setup script

With the Client ID and Secret from step 4, run [`tools/confluence-cloud-auth.sh`](../../../../tools/confluence-cloud-auth.sh)
from your Psoxy repo checkout. It exchanges them for an access token, looks up your Confluence
Cloud ID, and prints the exact `CLIENT_ID`/`CLIENT_SECRET`/Cloud ID values to use below - no need to
run the `curl` commands in [Manual steps](#manual-steps) yourself.

### Manual steps

If you'd rather not run the script, do the following yourself:

5. Obtain an access token directly (no browser/consent step needed - this is a machine-to-machine
   `client_credentials` exchange):

```shell
curl --request POST --url 'https://auth.atlassian.com/oauth/token' \
  --header 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=client_credentials' \
  --data-urlencode 'client_id=YOUR_CLIENT_ID' \
  --data-urlencode 'client_secret=YOUR_CLIENT_SECRET'
```

   You should see a JSON response like:

```json
{
  "access_token": "some short-lived access token",
  "expires_in": 3600,
  "token_type": "Bearer"
}
```

   (No `refresh_token` is returned or needed - request a new access token the same way whenever you
   need one; Psoxy does this automatically.)

6. Obtain the "Cloud ID" of your Confluence instance. Use the following command, with the
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

   Alternatively, an unauthenticated `GET https://your-site.atlassian.net/_edge/tenant_info` also
   returns the Cloud ID (`cloudId` field) for a given site, if you'd rather skip the token exchange
   just to look this up.

### Finishing up

Whether obtained via the script or manually, set the following variables in AWS System Manager
parameters store / GCP Cloud Secrets (if default implementation):
- `PSOXY_CONFLUENCE_CLOUD_CLIENT_ID` with `Client Id` value.
- `PSOXY_CONFLUENCE_CLOUD_CLIENT_SECRET` with `Client Secret` value.

Do **not** set `PSOXY_CONFLUENCE_CLOUD_REFRESH_TOKEN` - it's no longer used by this connector.

Add the Cloud ID (the `id` value from step 6, or printed by the script) as the value of the
`confluence_example_cloud_id` variable in your `connector_settings` in the `terraform.tfvars` file
of your Terraform configuration (e.g. `connector_settings = { confluence_example_cloud_id = "..." }`).
This will generate all the test URLs with a proper value.

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
