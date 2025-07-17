# GitHub

There are several connectors available for GitHub:

- [GitHub](github/README.md) - for GitHub Cloud Enterprise organizations hosted in github.com
- [GitHub non enterprise](github-non-enterprise/README.md) - for non-Enterprise GitHub Cloud (Free/Pro/Teams) organization hosted in github.com.
- [GitHub Enterprise Cloud](enterprise-server/README.md) - GitHub Enterprise instances hosted by github.com on behalf of your organization.
- [GitHub Copilot](copilot/README.md) - Copilot data from GitHub Enterprise instances hosted by github.com on behalf of your organization.


## Troubleshooting

There are THREE components to auth:
  - `PRIVATE_KEY` --> filled by you directly in your host's secret/parameter store,
  - `CLIENT_ID` --> filled by you directly in your host's secret/parameter store,
  - `github_copilot_installation_id` / `github_installation_id` --> fill in your `terraform.tfvars` file with the installation id of your GitHub App, which is used to generate the `REFRESH_URL` env var on the lambda/cloud function.

Common pitfalls:
  - creating a OAuth App instead of a GitHub App ; these are NOT the same thing; we use GitHub app as it acts independently of the user. Check this [link](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/differences-between-github-apps-and-oauth-apps) with more information about its differences. 

### 401 Unauthorized, No JSON body


`com.google.api.client.http.HttpResponseException: 401 Unauthorized `

See when the CLIENT_ID value is wrong. Double check this again "Client ID" value shown in your GitHub App's settings page.


### 404 Not Found
`{"message":"Not Found","documentation_url":"https://docs.github.com/rest/reference/apps#create-an-installation-access-token-for-an-app","status":"404"}`

Seen when installation id is wrong. Double check that your URL of the installation of the GitHub App in your organization is something like:

`https://github.com/organizations/Acme-org/settings/installations/12341234`

Then the installation id is `12341234`. You should fill this in your `terraform.tfvars` file as `github_copilot_installation_id`, which is then used to generate the `REFRESH_URL` env var on the lambda/cloud function.

### 404 Not Found

`{"message":"Integration not found","documentation_url":"https://docs.github.com/rest","status":"404"}`

Seen when you fill your installation id (8-digit number) *as* the value of CLIENT_ID (seems to be a 7-digit number if you use `App ID`; or a longer alphanumeric string if you use `Client ID` from your GitHub App Settings).
