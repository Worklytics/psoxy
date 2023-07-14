# Github

## Examples

  * [Example Rules](example-rules/github/github.yaml)
  * Example Data : [original](api-response-examples/github) | [sanitized](api-response-examples/github/sanitized)

## Steps to Connect

Follow the following steps:

1. From your organization, register a [GitHub App](https://docs.github.com/en/apps/creating-github-apps/registering-a-github-app/registering-a-github-app#registering-a-github-app)
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
- We assume that ALL the repositories are going to be list **should be owned by the organization, not the users**.
- Enterprise Cloud is required for this connector.

Apart from Github instructions please review the following:
- "Homepage URL" can be anything, not required in this flow but required by Github.
- Callback URL is required for next step, it can be any URL (http://localhost, for example);
- Webhooks check can be disabled as this connector is not using them
- Keep `Expire user authorization tokens` enabled, as GitHub documentation recommends
2. Once is created please generate a new client secret. We will both (clientId and clientSecret) in next steps.
3. Install the application in your organization.
   Go to your organization settings and then in "Developer Settings". Then, click on "Edit" for your "Github App" and once you are in the app settings, click on "Install App" and click on the "Install" button. Accept the permissions to install it in your whole organization.
4. Now is required to prepare the token for authentication. Following steps [for generating a user access token](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-user-access-token-for-a-github-app#using-the-web-application-flow-to-generate-a-user-access-token)
5. Update the variables with values obtained in previous step:
   - `PSOXY_GITHUB_CLOUD_ACCESS_TOKEN` secret variable with value of `access_token` received in previous response
   - `PSOXY_GITHUB_CLOUD_REFRESH_TOKEN` secret variable with value of `refresh_token` received in previous response
   - `PSOXY_GITHUB_CLOUD_CLIENT_ID` with `Client Id` value.
   - `PSOXY_GITHUB_CLOUD_CLIENT_SECRET` with `Client Secret` value.
   -

## Reference
These instructions have been derived from [worklytics-connector-specs](../../infra/modules/worklytics-connector-specs/main.tf); refer to that for definitive information.