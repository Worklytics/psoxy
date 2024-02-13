# GitHub

Availability: **BETA**

There are several connectors available for GitHub:

- [Github Free/Pro/Teams] - for non-Enterprise GitHub organization hosted in github.com.
- [Github Enterprise Cloud] - GitHub Enterprise instances hosted by github.com on behalf of your
  organization.
- [Github Enterprise Server] - similar to 'Cloud', but you must customize rules and API host;
  contact Worklytics for assistance.

## Examples

- [Example Rules](github.yaml)
- Example Data : [original](example-api-responses/original) |
  [sanitized](example-api-responses/sanitized)

## Steps to Connect

Both share the same configuration and setup instructions except Administration permission for Audit
Log events.

Follow the following steps:

1. Populate `github_organization` variable in Terraform with the name of your GitHub organization.
2. (Only if you are going to use GitHub Enterprise Server):

- You have to populate `github_enterprise_server_host` variable in Terraform with the hostname of
  your Github Enterprise Server (example: `github.your-company.com`).
- variable `github_organization` can be a list of organizations split by commas (ex:
  Worklytics,Worklytics2)

3. From your organization, register a
   [GitHub App](https://docs.github.com/en/apps/creating-github-apps/registering-a-github-app/registering-a-github-app#registering-a-github-app)
   with following permissions with **Read Only**:
   - Repository:
     - Contents: for reading commits and comments
     - Issues: for listing issues, comments, assignees, etc.
     - Metadata: for listing repositories and branches
     - Pull requests: for listing pull requests, reviews, comments and commits
   - Organization
     - Administration: (Only for GitHub Enterprise) for listing events from audit log
     - Members: for listing teams and their members

NOTES:

- We assume that ALL the repositories are going to be list **should be owned by the organization,
  not the users**.

Apart from GitHub instructions please review the following:

- "Homepage URL" can be anything, not required in this flow but required by GitHub.
- Webhooks check can be disabled as this connector is not using them
- Keep `Expire user authorization tokens` enabled, as GitHub documentation recommends

4. Once is created please generate a new `Private Key`.

5. It is required to convert the format of the certificate downloaded from PKCS#1 in previous step
   to PKCS#8. Please run following command:

```shell
openssl pkcs8 -topk8 -inform PEM -outform PEM -in {YOUR DOWNLOADED CERTIFICATE FILE} -out gh_pk_pkcs8.pem -nocrypt
```

**NOTES**:

- If the certificate is not converted to PKCS#8 connector will NOT work. You might see in logs a
  Java error `Invalid PKCS8 data.` if the format is not correct.
- Command proposed has been successfully tested on Ubuntu; it may differ for other operating
  systems.

6. Install the application in your organization. Go to your organization settings and then in
   "Developer Settings". Then, click on "Edit" for your "Github App" and once you are in the app
   settings, click on "Install App" and click on the "Install" button. Accept the permissions to
   install it in your whole organization.
7. Once installed, the `installationId` is required as it needs to be provided in the proxy as
   parameter for the connector in your Terraform module. You can go to your organization settings
   and click on `Third Party Access`. Click on `Configure` the application you have installed in
   previous step and you will find the `installationId` at the URL of the browser:

```
https://github.com/organizations/{YOUR ORG}/settings/installations/{INSTALLATION_ID}
```

Copy the value of `installationId` and assign it to the `github_installation_id` variable in
Terraform. You will need to redeploy the proxy again if that value was not populated before.

**NOTE**:

- If `github_installation_id` is not set, authentication URL will not be properly formatted and you
  will see _401: Unauthorized_ when trying to get an access token.
- If you see _404: Not found_ in logs please review the _IP restriction policies_ that your
  organization might have; that could cause connections from psoxy AWS Lambda/GCP Cloud Functions be
  rejected.

8. Update the variables with values obtained in previous step:
   - `PSOXY_GITHUB_CLIENT_ID` with `App ID` value. **NOTE**: It should be `App Id` value as we are
     going to use authentication through the App and **not** _client_id_.
   - `PSOXY_GITHUB_PRIVATE_KEY` with content of the `gh_pk_pkcs8.pem` from previous step. You could
     open the certificate with VS Code or any other editor and copy all the content _as-is_ into
     this variable.
9. Once the certificate has been uploaded, please remove {YOUR DOWNLOADED CERTIFICATE FILE} and
   `gh_pk_pkcs8.pem` from your computer or store it in a safe place.

## Reference

These instructions have been derived from
[worklytics-connector-specs](../../../infra/modules/worklytics-connector-specs/main.tf); refer to
that for definitive information.
