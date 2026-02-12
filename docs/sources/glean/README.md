# Glean **beta**

This connector provides access to Glean's REST API for retrieving user insights and entity listings from your Glean workspace. It enables you to analyze usage patterns and understand how your organization is engaging with Glean's search and AI capabilities.

## Authentication

A **Global Admin** in your Glean workspace needs to create a Glean-issued token. Follow the instructions at:
[Glean Authentication - Glean-issued Tokens](https://developers.glean.com/api-info/client/authentication/glean-issued)

### Required Scopes

Based on the endpoints used by this connector, the following scopes are required:

- **`INSIGHTS_READ`** - Required for the `/rest/api/v1/getinsights` endpoint to retrieve user activity insights
- **`PEOPLE_READ`** - Required for the `/rest/api/v1/listentities` endpoint to list users and teams

## Examples

- [Example Rules](glean.yaml)
- Example Data:
  - Insights: [original/insights-response.json](example-api-responses/original/insights-response.json) |
    [sanitized/insights-response.json](example-api-responses/sanitized/insights-response.json)
  - List Entities: [original/list-entities-response.json](example-api-responses/original/list-entities-response.json) |
    [sanitized/list-entities-response.json](example-api-responses/sanitized/list-entities-response.json)

See more examples in the `docs/sources/glean/example-api-responses` folder
of the [Psoxy repository](https://github.com/Worklytics/psoxy).

## Setup

### Prerequisites

- A **Global Admin** role in your Glean workspace
- Your Glean instance name (e.g., if your Glean URL is `acme.glean.com`, your instance name is `acme`)

### Configuration

1. **Instance Name**: Configure your Glean instance name in your Terraform variables:
   ```terraform
   glean_instance_name = "acme"  # replace with your actual instance name
   ```

2. **API Token**: Follow the authentication instructions above to create a Glean-issued token with the required scopes (`INSIGHTS_READ`, `PEOPLE_READ`)

3. **Deploy**: Deploy the proxy instance and configure the `ACCESS_TOKEN` secret with the token value from step 2

