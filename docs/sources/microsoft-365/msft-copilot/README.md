# Microsoft Copilot (alpha)

Connect Microsoft 365 Copilot data to Worklytics, enabling communication analysis and general
collaboration
insights based on collaboration via Microsoft 365 Copilot interaction. Includes user enumeration to
support fetching
mailboxes from each account.

**Note:** This connector is in alpha and it uses a *beta* endpoint in the API.

Please review the [Microsoft 365 README](../README.md) for general information applicable to
all Microsoft 365 connectors.

## Required Scopes

- [`User.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#userreadall)
- [
  `AiEnterpriseInteraction.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#aienterpriseinteractionread)

## Authentication

See the [Microsoft 365 Authentication](../README.md#authentication) section of the main README.

## Authorization

See the [Microsoft 365 Authorization](../README.md#authorization) section of the main README.

## Example Data

| API Endpoint                                                               | Example Response                                                                                                      | Sanitized Example Response                                                                      |
|----------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| `/beta/copilot/users/{id}/interactionHistory/getAllEnterpriseInteractions` | [original/response_beta.json](example-api-responses/original/response_beta.json)                                      | [sanitized/response.json](example-api-responses/sanitized/response_beta.json)                   |
| `/beta/copilot/users/{id}/interactionHistory/getAllEnterpriseInteractions` | [original/response_with_team_meeting_beta_.json](example-api-responses/original/response_with_team_meeting_beta.json) | [sanitized/response.json](example-api-responses/sanitized/response_with_team_meeting_beta.json) |
|

See more examples in the `docs/sources/microsoft-365/msft-copilot/example-api-responses` folder
of the [Psoxy repository](https://github.com/Worklytics/psoxy).

**NOTE for pseudonymizing app ids**

In case of `pseudonymize_app_ids` is set to `true`, the `id` will be tokenized. In such case and if
you want
to populate example variables like `example_msft_user_guid` in the example responses, you will need
first to
get a list of user and use the `id` in the variable. Using a plain user id without tokenization
might not work on endpoints that require
a tokenized user id.

## Example Rules

- [Example Rules](msft-copilot.yaml)
- [Example Rules: no User IDs](msft-copilot_no-userIds.yaml)
