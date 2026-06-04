# Glean

**Connector ID:** `glean`

**Availability:** Beta

This connector provides access to Glean's REST API for retrieving user insights and entity listings from your Glean workspace. It enables you to analyze usage patterns and understand how your organization is engaging with Glean's search and AI capabilities.

## Data Collected

This connector uses a **Global Token** with `X-Glean-ActAs` impersonation. Only **Super Admins** can create global tokens. Token scopes cannot be changed after creation, so configure them carefully at creation time.

See [Glean-issued token authentication](https://developers.glean.com/api-info/client/authentication/glean-issued) and [available scopes](https://developers.glean.com/api-info/client/authentication/glean-issued#available-scopes) for full details.

Based on the endpoints used by this connector, the following scopes are required:

- **`INSIGHTS_READ`** - Required for the `/rest/api/v1/insights` endpoint to retrieve user activity insights
- **`PEOPLE_READ`** - Required for the `/rest/api/v1/listentities` endpoint to list users and teams


| Endpoint | Description | Required Scope |
|---|---|---|
| `POST /rest/api/v1/insights` | Per-user activity insights (searches, AI answers, sessions, etc.) | `INSIGHTS` |
| `POST /rest/api/v1/listentities` | Users and teams in the organization | `ENTITIES` |

### Act-As Header

Glean-issued global tokens require an `X-Glean-ActAs` request header with the email of a user in your Glean workspace on every API call. See [Glean Authentication - Glean-issued Tokens](https://developers.glean.com/api-info/client/authentication/glean-issued).

Worklytics sends this header using the **Admin Email** (`ADMIN_EMAIL`) connector setting. Set it to the email address of the Global Admin who created the API credential. The proxy rules allow `X-Glean-ActAs` to pass through to Glean.

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

4. **Worklytics Admin Email**: When connecting in Worklytics, set **Admin Email** (`ADMIN_EMAIL`) to the email of the Global Admin who created the API credential

