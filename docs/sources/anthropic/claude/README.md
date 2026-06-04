# Claude

**Connector ID:** `claude`

**Availability:** Beta

Our Claude data connector uses the [Compliance API](https://platform.claude.com/docs/en/manage-claude/compliance-api-access) to import data about Users (accounts), Chats (work events), and Activities (audit logs) to Worklytics. This API is intended for compliance, security, and data privacy use.

## Data Collected

| Endpoint | Description |
|---|---|
| Activities | Audit log of actions taken by users in Claude (chat created, file uploaded, etc.) |
| Chats | Chat conversations, including metadata about the user and project |
| Chat Messages | Individual messages within a chat (message text is hashed) |
| Organization Users | User accounts in the organization |

## Steps to Connect

See [Anthropic's documentation](https://platform.claude.com/docs/en/manage-claude/compliance-api-access) for the latest, but as of early 2026 the following is required:

1. The **primary owner** of the parent organization must create a **Compliance Access Key** in [claude.ai > Organization settings > Data and privacy](https://claude.ai/admin-settings/data-privacy-controls) with the following scopes:
   - `read:compliance_activities`
   - `read:compliance_user_data`
   - `read:compliance_org_data`

2. Copy the key (starts with `sk-ant-api01-`) into the proxy as the `PSOXY_CLAUDE_ACCESS_TOKEN` parameter value in your proxy's host platform.

## Examples

- [Example Rules](claude.yaml)
- Example Data:
  - [activities-response.json](example-api-responses/original/activities-response.json) | [sanitized](example-api-responses/sanitized/activities-response.json)
  - [chats-response.json](example-api-responses/original/chats-response.json) | [sanitized](example-api-responses/sanitized/chats-response.json)
  - [chat-messages-response.json](example-api-responses/original/chat-messages-response.json) | [sanitized](example-api-responses/sanitized/chat-messages-response.json)
  - [organization-users-response.json](example-api-responses/original/organization-users-response.json) | [sanitized](example-api-responses/sanitized/organization-users-response.json)
