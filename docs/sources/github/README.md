# GitHub

Availability: **BETA**

There are several connectors available for GitHub:

- [GitHub Free/Pro/Teams] - for non-Enterprise GitHub organization hosted in github.com.
- [GitHub Enterprise Cloud] - GitHub Enterprise instances hosted by github.com on behalf of your
  organization.
- [GitHub Copilot] - Copilot data from GitHub Enterprise instances hosted by github.com on behalf of your
  organization.
  contact Worklytics for assistance.

## Authentication workflow

The connector uses a GitHub App to authenticate and access the data.
- For Enterprise Server, you must generate a [user access token](https://docs.github.com/en/enterprise-server@3.11/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-user-access-token-for-a-github-app#generating-a-user-access-token-when-a-user-installs-your-app).
- For Cloud, including Free/Pro/Teams/Enterprise, you must provide an [installation token](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-an-installation-access-token-for-a-github-app) for authentication.

## Examples

- [Example Rules](github.yaml)
- Example Data : [original](example-api-responses/original) |
  [sanitized](example-api-responses/sanitized)

## GitHub Cloud (Free, Teams, Professional, Enterprise): Steps to Connect

Both share the same configuration and setup instructions except Administration permission for Audit
Log events.

Follow the following steps:

1. Populate `github_organization` variable in Terraform with the name of your GitHub organization.

2. From your organization, register a
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

3. Once is created please generate a new `Private Key`.

4. It is required to convert the format of the certificate downloaded from PKCS#1 in previous step
   to PKCS#8. Please run following command:

```shell
openssl pkcs8 -topk8 -inform PEM -outform PEM -in {YOUR DOWNLOADED CERTIFICATE FILE} -out gh_pk_pkcs8.pem -nocrypt
```

**NOTES**:

- If the certificate is not converted to PKCS#8 connector will NOT work. You might see in logs a
  Java error `Invalid PKCS8 data.` if the format is not correct.
- Command proposed has been successfully tested on Ubuntu; it may differ for other operating
  systems.

5. Install the application in your organization. Go to your organization settings and then in
   "Developer Settings". Then, click on "Edit" for your "Github App" and once you are in the app
   settings, click on "Install App" and click on the "Install" button. Accept the permissions to
   install it in your whole organization.
6. Once installed, the `installationId` is required as it needs to be provided in the proxy as
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

## GitHub Copilot: Steps to connect

Follow the following steps:

1. Populate `github_organization` variable in Terraform with the name of your GitHub organization.

2. From your organization, register a [GitHub App](https://docs.github.com/en/apps/creating-github-apps/registering-a-github-app/registering-a-github-app#registering-a-github-app)
   with following permissions with **Read Only**:
    - Organization
        - Administration: for listing events from audit log
        - Members: for listing teams and their members
        - GitHub Copilot Business: for listing Copilot usage

NOTES:
- Enterprise Cloud is required for this connector.

Apart from GitHub instructions please review the following:
- "Homepage URL" can be anything, not required in this flow but required by GitHub.
- Webhooks check can be disabled as this connector is not using them
- Keep `Expire user authorization tokens` enabled, as GitHub documentation recommends
3. Once created, generate a new `Private Key`.
4. It is required to convert the format of the certificate downloaded from PKCS#1 in previous step to PKCS#8. Please run following command:
```shell
openssl pkcs8 -topk8 -inform PEM -outform PEM -in {YOUR DOWNLOADED CERTIFICATE FILE} -out gh_copilot_pk_pkcs8.pem -nocrypt
```

**NOTES**:
- If the certificate is not converted to PKCS#8 connector will NOT work. You might see in logs a Java error `Invalid PKCS8 data.` if the format is not correct.
- Command proposed has been successfully tested on Ubuntu; it may differ for other operating systems.

5. Install the application in your organization.
   Go to your organization settings and then in "Developer Settings". Then, click on "Edit" for your "GitHub App" and once you are in the app settings, click on "Install App" and click on the "Install" button. Accept the permissions to install it in your whole organization.
6. Once installed, the `installationId` is required as it needs to be provided in the proxy as parameter for the connector in your Terraform module. You can go to your organization settings and
   click on `Third Party Access`. Click on `Configure` the application you have installed in previous step and you will find the `installationId` at the URL of the browser:
```
https://github.com/organizations/{YOUR ORG}/settings/installations/{INSTALLATION_ID}
```
Copy the value of `installationId` and assign it to the `github_copilot_installation_id` variable in Terraform. You will need to redeploy the proxy again if that value was not populated before.

**NOTE**:
- If `github_copilot_installation_id` is not set, authentication URL will not be properly formatted and you will see *401: Unauthorized* when trying to get an access token.
- If you see *404: Not found* in logs please review the *IP restriction policies* that your organization might have; that could cause connections from psoxy AWS Lambda/GCP Cloud Functions be rejected.

7. Update the variables with values obtained in previous step:
    - `PSOXY_GITHUB_CLIENT_ID` with `App ID` value. **NOTE**: It should be `App Id` value as we are going to use authentication through the App and **not** *client_id*.
    - `PSOXY_GITHUB_PRIVATE_KEY` with content of the `gh_pk_pkcs8.pem` from previous step. You could open the certificate with VS Code or any other editor and copy all the content *as-is* into this variable.
8. Once the certificate has been uploaded, please remove {YOUR DOWNLOADED CERTIFICATE FILE} and `gh_copilot_pk_pkcs8.pem` from your computer or store it in a safe place.


## GitHub Enterprise Server: Steps to connect

We provide a [helper script](../../../tools/github-enterprise-server-auth.sh) to set up the connector, which will guide you through the steps
below and automate some of them. Alternatively, you can follow the steps below directly:

1. You have to populate:
    - `github_enterprise_server_host` variable in Terraform with the hostname of your GitHub
      Enterprise Server (example: `github.your-company.com`). This host should be accessible from
      the proxy instance function, as the connector will need to reach it.
    - `github_organization` variable in Terraform with the name of your organization in GitHub
      Enterprise Server. You can put more than one, just split them in commas (example: `org1,org2`).
2. From your organization, register a [GitHub App](https://docs.github.com/en/enterprise-server@3.11/apps/creating-github-apps/registering-a-github-app/registering-a-github-app#registering-a-github-app)
   with following permissions with **Read Only**:
    - Repository:
      - Contents: for reading commits and comments
      - Issues: for listing issues, comments, assignees, etc.
      - Metadata: for listing repositories and branches
      - Pull requests: for listing pull requests, reviews, comments and commits
    - Organization
      - Administration: for listing events from audit log
      - Members: for listing teams and their members

NOTES:
- We assume that ALL the repositories are going to be listed **should be owned by the organization, not the users**.

Apart from GitHub instructions please review the following:
- "Homepage URL" can be anything, not required in this flow but required by GitHub.
- "Callback URL" can be anything, but we recommend something like `http://localhost` as we will need it for the redirect as part of the authentication.
- Webhooks check can be disabled as this connector is not using them
- Keep `Expire user authorization tokens` enabled, as GitHub documentation recommends
3. Once is created please generate a new `Client Secret`.
4. Copy the `Client ID` and copy in your browser following URL, replacing the `CLIENT_ID` with the value you have just copied:
```
https://your-github-host/login/oauth/authorize?client_id={YOUR CLIENT ID}
```
5. The browser will ask you to accept permissions and then it will redirect you with to the previous `Callback URL` set as part of the application.
   The URL should look like this: `https://localhost/?code=69d0f5bd0d82282b9a11`.
6. Copy the value of `code` and run the following URL replacing in the placeholders the values of `Client ID` and `Client Secret`:
```
curl --location --request POST 'https://your-github-host/login/oauth/access_token?client_id={YOUR CLIENT ID}&client_secret={YOUR CLIENT SECRET}&code={YOUR CODE}' --header 'Content-Type: application/json' --header 'Accept: application/json'
```
The response will be something like:

```json
{
  "access_token":"ghu_...",
  "expires_in":28800,
  "refresh_token":"ghr_...",
  "refresh_token_expires_in":15724800,
  "token_type":"bearer",
  "scope":""
}
```
You will need to copy the value of the `refresh_token`.

**NOTES**:
- `Code` can be used once, so if you need to repeat the process you will need to generate a new one.

7. Update the variables with values obtained in previous step:
    - `psoxy_GITHUB_ENTERPRISE_SERVER_CLIENT_ID` with `Client Id` value.
    - `psoxy_GITHUB_ENTERPRISE_SERVER_CLIENT_SECRET` with `Client Secret` value.
    - `psoxy_GITHUB_ENTERPRISE_SERVER_REFRESH_TOKEN` with the `refresh_token`.

## Reference

These instructions have been derived from
[worklytics-connector-specs](../../../infra/modules/worklytics-connector-specs/main.tf); refer to
that for definitive information.
