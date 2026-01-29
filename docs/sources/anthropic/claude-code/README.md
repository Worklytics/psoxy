# Anthropic

## Claude Code **BETA**

Examples data:
- [Example Rules](claude-code.yaml)
- Example Data:
  - [list-users.json](example-api-responses/original/list-users.json) | [sanitized/example_data.json](example-api-responses/sanitized/list-users.json)
  - [claude-code-usage-report.json](example-api-responses/original/claude-code-usage-report.json) | [sanitized/claude-code-usage-report.json](example-api-responses/sanitized/claude-code-usage-report.json)

See [Claude documentation](https://platform.claude.com/docs/en/build-with-claude/administration-api); as per Jan 2026, the following instructions are relevant for configuring the Worklytics Connector Proxy to connect to Claude's Admin API.

1. An admin user from the organization
2. The key created should have `admin`[https://platform.claude.com/docs/en/build-with-claude/administration-api#organization-roles-and-permissions] permission
3. Copy the API key into the proxy, as `${path_to_instance_parameters}ADMIN_API_KEY` parameter value in your proxy's host platform.
