# Claude

**BETA**

Our Claude data connector uses the [Compliance API](https://platform.claude.com/docs/en/claude-api/compliance) to import data about Users (accounts), Chats (work events), and Activities (audit logs) to Worklytics. This API is intended for compliance, security, and data privacy use.

## Data Collected

| Endpoint | Description |
|---|---|
| Activities | Audit log of actions taken by users in Claude (chat created, file uploaded, etc.) |
| Chats | Chat conversations, including metadata about the user and project |
| Chat Messages | Individual messages within a chat (message text is hashed) |
| Organization Users | User accounts in the organization |

## Steps to Connect

See [Anthropic's documentation](https://platform.claude.com/docs/en/build-with-claude/administration-api) for the latest, but as of early 2026 the following is required:

1. An admin user from the organization must generate an API key with `admin` permission in the [Anthropic Console](https://console.anthropic.com/).

2. Copy the API key into the proxy as the `PSOXY_CLAUDE_ACCESS_TOKEN` parameter value in your proxy's host platform.

## Examples

- [Example Rules](claude.yaml)
- Example Data:
  - [activities-response.json](example-api-responses/original/activities-response.json) | [sanitized](example-api-responses/sanitized/activities-response.json)
  - [chats-response.json](example-api-responses/original/chats-response.json) | [sanitized](example-api-responses/sanitized/chats-response.json)
  - [chat-messages-response.json](example-api-responses/original/chat-messages-response.json) | [sanitized](example-api-responses/sanitized/chat-messages-response.json)
  - [organization-users-response.json](example-api-responses/original/organization-users-response.json) | [sanitized](example-api-responses/sanitized/organization-users-response.json)
