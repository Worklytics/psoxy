# Confluence Cloud

**Connector ID:** `confluence-cloud`

**Availability:** Beta

NOTE: These instructions are derived from [worklytics-connector-specs](../../../../infra/modules/worklytics-connector-specs/main.tf); refer to that for definitive information.

## Examples

- [Example Rules](confluence.yaml)
- Example Data : [original/content_search.json](example-api-responses/original/content_search.json) |
  [sanitized/content_search.json](example-api-responses/sanitized/content_search.json)

Example API call path parameters (uppercase placeholders in generated test calls):

- `{CLOUD_ID}`: Confluence Cloud id (`connector_settings.confluence_example_cloud_id`, see
  [Optional: Cloud ID](#optional-cloud-id-for-generating-exampletest-api-call-urls) below).
- `{GROUP_ID}`: a group id from `GET .../wiki/rest/api/group`.
- `{ATTACHMENT_ID}`: an attachment id from content search / v2 APIs (the attachments-versions rule names this path param `{pageId}`; fill it with the attachment id).
- `{BLOGPOST_ID}` / `{PAGE_ID}` / `{CUSTOM_CONTENT_ID}` / `{COMMENT_ID}`: ids of the corresponding Confluence objects whose versions you want to list. `{version}` in the rules is `v2` in the examples.

See more examples in the `docs/sources/atlassian/confluence/example-api-responses` folder
of the [Psoxy repository](https://github.com/Worklytics/psoxy).

## Prerequisites

Confluence Cloud through Psoxy authenticates using an [Atlassian OAuth 2.0 credential for a service
account](https://support.atlassian.com/user-management/docs/create-oauth-2-0-credential-for-service-accounts/)
(a "2LO"/`client_credentials` grant). This requires:

- Your Atlassian organization must be on the **centralized user management** experience
  (`admin.atlassian.com`). Org admin access is required to create the service account and its
  credential.
- A **service account** (orgs get 5 free; more available via Atlassian Guard/Enterprise) granted a
  role (e.g. `User`) on the Confluence site you want to connect, sufficient to produce the scopes
  below.

Granting the service account a role on Confluence grants an OAuth 2.0 credential created for it a
set of scopes, which should include the following (used by Psoxy) - confirm this on the review
screen when creating the credential (step 6 below), since available roles/scopes can vary by plan:
| Scope | Purpose |
| --- | --- |
| `read:blogpost:confluence` | for getting [blogposts and their versions](https://developer.atlassian.com/cloud/confluence/rest/v2/api-group-version/#api-blogposts-id-versions-get) |
| `read:comment:confluence` | for getting [footer-comments](https://developer.atlassian.com/cloud/confluence/rest/v2/api-group-version/#api-footer-comments-id-versions-get) and [inline-comments](https://developer.atlassian.com/cloud/confluence/rest/v2/api-group-version/#api-inline-comments-id-versions-get) and their versions |
| `read:group:confluence` | for getting [groups](https://developer.atlassian.com/cloud/confluence/rest/v1/api-group-group/#api-wiki-rest-api-group-get) |
| `read:user:confluence` | for getting [users from groups](https://developer.atlassian.com/cloud/confluence/rest/v1/api-group-group/#api-wiki-rest-api-group-groupid-membersbygroupid-get) |
| `read:space:confluence` | for getting [spaces](https://developer.atlassian.com/cloud/confluence/rest/v2/api-group-space/#api-spaces-get) |
| `read:attachment:confluence` | for getting [attachments and their versions](https://developer.atlassian.com/cloud/confluence/rest/v2/api-group-version/#api-attachments-id-versions-get) |
| `read:page:confluence` | for getting [pages and their versions](https://developer.atlassian.com/cloud/confluence/rest/v2/api-group-version/#api-pages-id-versions-get) |
| `read:task:confluence` | for getting [tasks](https://developer.atlassian.com/cloud/confluence/rest/v2/api-group-task/#api-tasks-get) |
| `read:content-details:confluence` | for using [content search endpoint](https://developer.atlassian.com/cloud/confluence/rest/v1/api-group-search/#api-wiki-rest-api-search-get) |

You will need a web browser for the one-time service account/credential setup in
`admin.atlassian.com`.

## Setup Instructions

1. Go to [https://admin.atlassian.com](https://admin.atlassian.com), select your organization, then
   "Directory" > "Service accounts".

   ![Service accounts](../img/admin_service_accounts.png)

2. Click "Create a service account" (or re-use an existing one, e.g. if you've already set one up
   for [Jira Cloud](../jira/README.md)). Give it a name (6-30 characters, e.g. `proxy_sa`) and an
   optional description, then continue to "Select app roles".

   ![Name service account](../img/admin_new_service_account.png)

   **NOTE** You can't change a service account's email after creation, and orgs get 5 free service
   accounts before requiring Atlassian Guard/Enterprise - plan the account name accordingly.

3. In the app roles table, find "Confluence" and set its role (e.g. `User`). Finish creating the
   account.

   ![Select app role](../img/admin_roles_for_sa.png)

4. From the service account's page, click "Create credentials".

   ![Service account created](../img/admin_created_service_account.png)

5. Choose "OAuth 2.0" as the authentication type (the recommended option) and continue.

   ![Choose authentication type](../img/admin_auth2_type.png)

6. On the review screen, confirm the listed scopes match the list in Prerequisites above. (The
   screenshot below is from the Jira flow, shown as an illustration of the review step - yours will
   read "Credentials for Confluence" and list the Confluence scopes instead.)

   ![Review OAuth credential (illustrative)](../img/admin_oauth_credentials_jira.png)

   **NOTE** Scopes are set by the app role granted in step 3; you can't hand-pick scopes here, and
   once the credential is created you can't modify it - revoke and re-create it if you need to
   change the role/scopes later.

7. Create the credential, then immediately copy the "Client ID" and "Client secret" - they are
   **shown only once** and can't be recovered afterwards.

   ![Client ID and secret](../img/admin_oauth_credentials_id_and_secret.png)

8. Set the following variables in AWS System Manager parameters store / GCP Cloud Secrets (if
   default implementation):
   - `PSOXY_CONFLUENCE_CLOUD_CLIENT_ID` with the "Client ID" value.
   - `PSOXY_CONFLUENCE_CLOUD_CLIENT_SECRET` with the "Client secret" value.

That's all that's needed to run the connector - no refresh token, and no further interaction with
Atlassian is required afterwards (Psoxy exchanges the client ID/secret for short-lived access
tokens automatically, as needed).

### Optional: Cloud ID, for generating example/test API call URLs

The Confluence Cloud ID is **not** required for the connector to authenticate or run - it's only
used by Terraform to generate example/test API call URLs. If you want those, run
[`tools/confluence-cloud-id.sh`](../../../../tools/confluence-cloud-id.sh) (give it your site's URL,
e.g. `https://your-site.atlassian.net`) and set the Cloud ID it prints as the value of the
`confluence_example_cloud_id` variable in your `connector_settings` in `terraform.tfvars` (e.g.
`connector_settings = { confluence_example_cloud_id = "..." }`).

If you'd rather not use the script, you can get the same value yourself - no credentials needed:

```shell
curl https://your-site.atlassian.net/_edge/tenant_info
```

which returns `{"cloudId": "SOME-UUID", ...}` (or just open that URL in a browser). Use the
`cloudId` value the same way as above.

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
