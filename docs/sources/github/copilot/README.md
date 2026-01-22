# GitHub Copilot

Availability: **ALPHA**

## Authentication workflow

The connector uses a GitHub App to authenticate and access the data. You must provide an [installation token](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-an-installation-access-token-for-a-github-app) for authentication.

## Examples

- [Example Rules](github-copilot.yaml)
- Example Data:
  - [original/user.json](example-api-responses/original/user.json) |
    [sanitized/user.json](example-api-responses/sanitized/user.json)

## GitHub Copilot: Steps to connect

Follow the following steps:

1. Populate `github_organization` variable in Terraform with the name of your GitHub organization.

2. Register a [GitHub App](https://docs.github.com/en/apps/creating-github-apps/registering-a-github-app/registering-a-github-app#registering-a-github-app) *owned by your Organization, not you as an individual* with following permissions with **Read Only**:
    - Organization
        - Administration: for listing "copilot" events from audit log
        - Members: for listing teams and their members
        - GitHub Copilot Business: for listing Copilot usage

NOTES:
- Enterprise Cloud is required for this connector.

Apart from GitHub instructions please review the following:
- "Homepage URL" can be anything, not required in this flow but required by GitHub.
- Webhooks check can be disabled as this connector is not using them
- Keep `Expire user authorization tokens` enabled, as GitHub documentation recommends
3. Once created, generate a new `Private Key`.
4. Install the application in your organization.  Go to your organization settings and then in "Developer Settings". Then, click on "Edit" for your "GitHub App" and once you are in the app settings, click on "Install App" and click on the "Install" button. Accept the permissions to install it in your whole organization.
5. Once installed, the `installationId` is required as it needs to be provided in the proxy as parameter for the connector in your Terraform module. You can go to your organization settings and click on `Third Party Access`. Click on `Configure` the application you have installed in previous step and you will find the `installationId` at the URL of the browser:
```
https://github.com/organizations/{YOUR ORG}/settings/installations/{INSTALLATION_ID}
```
Copy the value of `installationId` and assign it to the `github_copilot_installation_id` variable in Terraform. You will need to redeploy the proxy again if that value was not populated before.

**NOTE**:
- If `github_copilot_installation_id` is not set, authentication URL will not be properly formatted and you will see *401: Unauthorized* when trying to get an access token.
- If you see *404: Not found* in logs please review the *IP restriction policies* that your organization might have; that could cause connections from psoxy AWS Lambda/GCP Cloud Functions be rejected.

6. Update the configuration parameters in your host platform with values obtained in previous step:
    - `PSOXY_GITHUB_COPILOT_CLIENT_ID` with `Client ID` value from the application you have created.
    - `PSOXY_GITHUB_COPILOT_PRIVATE_KEY` with content of the key file downloaded in step 4. In a terminal on MacOS, run `cat key_file.pem | pbcopy` to copy the key to your clipboard, then paste it as the value of the parameter via the web console of your host platforms parameter store.
7. Once your key value has been filled in the parameter store, you should DELETE the key file from your machine.


You can repeat steps 3,6,7 at any time to rotate your key.


## Reference

These instructions have been derived from [worklytics-connector-specs](../../../infra/modules/worklytics-connector-specs/main.tf); refer to that for definitive information.
