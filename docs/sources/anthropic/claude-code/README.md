# Claude Code

**Connector ID:** `claude-code`

**Availability:** Beta

Examples data:
- [Example Rules](claude-code.yaml)
- Example Data:
  - [list-users.json](example-api-responses/original/list-users.json) | [sanitized/example_data.json](example-api-responses/sanitized/list-users.json)
  - [claude-code-usage-report.json](example-api-responses/original/claude-code-usage-report.json) | [sanitized/claude-code-usage-report.json](example-api-responses/sanitized/claude-code-usage-report.json)

See [Claude documentation](https://platform.claude.com/docs/en/manage-claude/admin-api) for the latest. The following is required to connect to Claude's Admin API.

1. An organization member with the [`admin` role](https://platform.claude.com/docs/en/manage-claude/admin-api#organization-roles-and-permissions) must create an **Admin API key** in [Claude Console > Settings > Admin keys](https://platform.claude.com/settings/admin-keys). Only admins can create Admin API keys.
2. Copy the key (starts with `sk-ant-admin01-`) into the proxy, as `${path_to_instance_parameters}ADMIN_API_KEY` parameter value in your proxy's host platform.
