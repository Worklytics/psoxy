# Glean

**Connector ID:** `glean`

**Availability:** Beta

This connector provides access to Glean's REST API for retrieving user insights and entity listings from your Glean workspace. It enables you to analyze usage patterns and understand how your organization is engaging with Glean's search and AI capabilities.

## Data Collected

This connector uses a **Global Token** with `X-Glean-ActAs` impersonation. Only **Super Admins** can create global tokens. Token scopes cannot be changed after creation, so configure them carefully at creation time.

See [Glean-issued token authentication](https://developers.glean.com/api-info/client/authentication/glean-issued) and [available scopes](https://developers.glean.com/api-info/client/authentication/glean-issued#available-scopes) for full details.

| Endpoint | Description | Required Scope |
|---|---|---|
| `POST /rest/api/v1/insights` | Per-user activity insights (searches, AI answers, sessions, etc.) | `INSIGHTS` |
| `POST /rest/api/v1/listentities` | Users and teams in the organization | `ENTITIES` |

## Setup

### Prerequisites

- A **Super Admin** role in your Glean workspace (required to create a Global Token)
- Your Glean instance subdomain (e.g., if your Glean URL is `acme.glean.com`, your instance subdomain is `acme`)

### Configuration

1. **Instance Subdomain**: Configure your Glean instance subdomain in your Terraform variables:
   ```terraform
   connector_settings = {
     glean_instance_subdomain = "acme"  # replace with your actual instance subdomain
   }
   ```

2. **API Token**: Follow the steps in the instructions to create a Glean-issued Global Token with the required scopes (`INSIGHTS`, `ENTITIES`)

3. **Deploy**: Deploy the proxy instance and configure the `ACCESS_TOKEN` secret with the token value from step 2

## Examples

- [Example Rules](glean.yaml)
- Example Data:
  - Insights: [original/insights-response.json](example-api-responses/original/insights-response.json) |
    [sanitized/insights-response.json](example-api-responses/sanitized/insights-response.json)
  - List Entities: [original/list-entities-response.json](example-api-responses/original/list-entities-response.json) |
    [sanitized/list-entities-response.json](example-api-responses/sanitized/list-entities-response.json)
