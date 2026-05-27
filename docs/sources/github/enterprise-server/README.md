# GitHub Enterprise Server

Availability: **GA**

## Authentication workflow

The connector uses a GitHub App to authenticate and access the data. You must generate a [user access token](https://docs.github.com/en/enterprise-server@3.11/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-user-access-token-for-a-github-app#generating-a-user-access-token-when-a-user-installs-your-app).

## Examples

- [Example Rules](github-enterprise-server.yaml)
- Example Data:
  - [original/user.json](example-api-responses/original/user.json) |
    [sanitized/user.json](example-api-responses/sanitized/user.json)

## GitHub Enterprise Server: Steps to connect

We provide a [helper script](../../../../tools/github-enterprise-server-auth.sh) to set up the connector, which will guide you through the steps below and automate some of them. Alternatively, you can follow the steps below directly:

1. You have to populate:
    - `github_enterprise_server_host` variable in Terraform with the hostname of your GitHub Enterprise Server (example: `github.your-company.com`). This host should be accessible from the proxy instance function, as the connector will need to reach it.
    - `github_organization` variable in Terraform with the name of your organization in GitHub Enterprise Server. You can put more than one, just split them with commas (example: `org1,org2`).
2. From your organization, register a [GitHub App](https://docs.github.com/en/enterprise-server@3.11/apps/creating-github-apps/registering-a-github-app/registering-a-github-app#registering-a-github-app) with the following permissions, all set to **Read Only**:
    - Repository:
      - Contents: for reading commits and comments
      - Issues: for listing issues, comments, assignees, etc.
      - Metadata: for listing repositories and branches
      - Pull requests: for listing pull requests, reviews, comments and commits
    - Organization:
      - Administration: for listing events from audit log
      - Members: for listing teams and their members

NOTES:
- We assume that ALL the repositories to be listed **should be owned by the organization, not the users**.

Apart from GitHub instructions, please review the following:
- "Homepage URL" can be anything, not required in this flow but required by GitHub.
- "Callback URL" can be anything, but we recommend something like `http://localhost` as we will need it for the redirect as part of the authentication.
- Webhooks check can be disabled as this connector is not using them.
- Keep `Expire user authorization tokens` enabled, as GitHub documentation recommends.
3. Once created, please generate a new `Client Secret`.
4. Copy the `Client ID` and copy in your browser the following URL, replacing the `CLIENT_ID` with the value you have just copied:

```
https://your-github-host/login/oauth/authorize?client_id={YOUR CLIENT ID}
```

5. The browser will ask you to accept permissions and then it will redirect you with to the previous `Callback URL` set as part of the application. The URL should look like this: `https://localhost/?code=69d0f5bd0d82282b9a11`.
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

These instructions have been derived from [worklytics-connector-specs](../../../../infra/modules/worklytics-connector-specs/main.tf); refer to that for definitive information.
