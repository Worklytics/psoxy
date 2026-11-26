# ChatGPT

**Connector ID:** `chatgpt-enterprise`

**Availability:** Beta

***YMMV.*** *ChatGPT's APIs, like many other AI platforms, have been evolving rapidly. Our instructions may be out of date, or may reflect the behavior of a different subscription, tenant, or tier than yours. Please let us know if you encounter an issue.*

## ChatGPT Enterprise via Compliance API

This API allows ChatGPT Enterprise administrators to observe and remove data from their ChatGPT Enterprise workspaces. It is intended for compliance, security, and data privacy use.  The get endpoints provide time-indexed access to ChatGPT Enterprise data.
Administrators can use it to retrieve this data for archival or Data Loss Prevention purposes as part of a compliance or security program.

This API is designed for regularly downloading data diffs to synchronize an offline database for data compliance, rather than mass export. The initial data sync will take longer, but subsequent syncs will be faster as only the diffs need to be downloaded.

A workspace **Owner** creates the key at [https://admin.openai.com](https://admin.openai.com): select the ChatGPT workspace, then **Credentials → Admin keys → Create new admin key**. Choose **Custom** permissions. See [Endpoints and required scopes](#endpoints-and-required-scopes) below for exactly which scope each endpoint needs, [Managing Admin keys in Admin Console](https://help.openai.com/en/articles/20001407-managing-admin-keys-in-admin-console), and the [Admin API reference](https://chatgpt.com/public/admin/api-reference#tag/Introduction) (authorization is in the Introduction). Only an Owner can grant **Conversation messages** or the broad compliance permission; an Admin can grant narrower categories such as audit, authentication, or app logs, but a key limited to those does not include conversation usage, project metadata, or the user roster.

**Admin keys** are available on ChatGPT Enterprise, Edu, and Healthcare workspaces. If **Credentials** lists only **Access Tokens**, this workspace does not have Admin keys. An Access Token cannot authorize these compliance calls. Confirm the top-left selector is the ChatGPT workspace, not the organization, and that you are an Owner of that workspace.

That Admin key is a different credential from a [Platform API key](https://platform.openai.com/api-keys) and from an API Platform organization Admin key (those call `/v1/organization/…`).

**Note on Message Authorship:** The specific encoding of `bot` vs `user` messages in the compliance API is currently undocumented by OpenAI. Psoxy rule datasets support the observed `author.type` field and the earlier best-guess `author.role` field for conditional filtering algorithms.

**Note on legacy `conversations` endpoints:** OpenAI deprecated the synchronous `/conversations` and `/conversations/{CONVERSATION_ID}/messages` compliance API paths in favor of the `/logs` API (see [Example Rules](chatgpt-enterprise.yaml)). OpenAI reports those stateful routes were **removed on June 5, 2026**; confirm current status in [OpenAI's Compliance Platform documentation](https://help.openai.com/en/articles/9261474). Psoxy rules still allow the legacy paths so already-deployed proxies do not reject leftover traffic; new integrations should use the `/logs` endpoints only. Worklytics itself no longer calls `/conversations` — conversation usage comes from `/logs` (`event_type=CONVERSATION_MESSAGE`) for both direct and proxy connections.

Psoxy allow-lists the compliance paths below and sanitizes whatever Worklytics requests. It does not call ChatGPT on its own.

For a Psoxy connection (a proxy output bucket is set), Worklytics reads usage from `GET /v1/compliance/workspaces/{workspaceId}/logs` and `GET .../logs/{id}` (see [Compliance Logs event types](#compliance-logs-event-types) for which event types). It also requests `GET .../projects` on every fetch unless the connection's `fetchProjects` option is `false`, and `GET .../users` to sync the workspace's user roster (no option to skip this today — see below). `/projects` supplies project-creation items only; `/users` supplies the roster used to match ChatGPT users against your People directory.

## Endpoints and required scopes

| Endpoint | Data | Required scope |
|---|---|---|
| `GET /v1/compliance/workspaces/{workspaceId}/users` | Workspace user roster | `chatgpt.enterprise.compliance_export.read` |
| `GET /v1/compliance/workspaces/{workspaceId}/projects` | Project-creation metadata (optional; see `fetchProjects` below) | `chatgpt.enterprise.compliance_export.read` |
| `GET /v1/compliance/workspaces/{workspaceId}/logs` and `.../logs/{logFileId}` | Usage data — see [Compliance Logs event types](#compliance-logs-event-types) below | `chatgpt.enterprise.compliance_logs_platform.read` (broad), or `chatgpt.enterprise.compliance_export.read` |
| `GET /v1/compliance/organizations/{organizationId}/logs` and `.../logs/{logFileId}` | Hourly cost/usage data (`event_type=COSTS`, optional, see step 5 below) | `chatgpt.enterprise.compliance_logs_platform.read` (broad), or `chatgpt.enterprise.compliance_export.read` |

`chatgpt.enterprise.compliance_export.read` is the only scope `/users` and `/projects` accept — per OpenAI's OpenAPI spec there is no separate bare `compliance_export` (no `.read`) scope. That same scope also works for `/logs` — OpenAI's spec lists it as a valid alternative security scheme there, not a separate legacy-only scope. So a key granted only `compliance_export.read` covers every endpoint in this table. A key granted only `compliance_logs_platform.read` (or the fine-grained per-event-type scopes below) covers `/logs` but not `/users` or `/projects`.

### Compliance Logs event types

Admin keys are authorized per `event_type` requested from `/logs`. Worklytics fetches all of the event types below by default. The broad `chatgpt.enterprise.compliance_logs_platform.read` scope above covers every row; grant a row's fine-grained scope instead only if you want least-privilege access to a single event type without using that broad scope (or `compliance_export.read`):

| Event type | Data | Scope (if not using the broad/`compliance_export.read` scope) |
|---|---|---|
| `CONVERSATION_MESSAGE` | Conversation usage — this connector's core data | `chatgpt.enterprise.compliance_logs_platform.conversation_message.read` |
| `CODEX_LOG` | Codex usage — core data if the workspace uses Codex | `chatgpt.enterprise.compliance_logs_platform.codex_log.read` |
| `APP_LOG` | App usage | `chatgpt.enterprise.compliance_logs_platform.app_log.read` |
| `CHATGPT_PLUGIN_SPREADSHEET` | Spreadsheet plugin usage | `chatgpt.enterprise.compliance_logs_platform.chatgpt_plugin_spreadsheet.read` |
| `CHATGPT_PLUGIN_PRESENTATION` | Presentation plugin usage | `chatgpt.enterprise.compliance_logs_platform.chatgpt_plugin_presentation.read` |
| `CHATGPT_PLUGIN_DOCUMENT` | Document plugin usage | `chatgpt.enterprise.compliance_logs_platform.chatgpt_plugin_document.read` |
| `CUSTOM_AGENTS_LOG` | Custom agents usage | `chatgpt.enterprise.compliance_logs_platform.custom_agents_log.read` |
| `AUDIT_LOG` | Audit log events | `chatgpt.enterprise.compliance_logs_platform.audit_log.read` |
| `AGENT_TRACES` | Agent trace events | `chatgpt.enterprise.compliance_logs_platform.agent_traces.read` |
| `CODEX_TURN` | Codex turn-level events | `chatgpt.enterprise.compliance_logs_platform.codex_turn.read` |
| `CODEX_SECURITY_LOG` | Codex security events | `chatgpt.enterprise.compliance_logs_platform.codex_security_log.read` |
| `COSTS` | Hourly cost/usage data (organization-scoped, see step 5 below) | `chatgpt.enterprise.compliance_logs_platform.costs.read` |

Shown in the Admin portal's **Create Admin API key** modal: the broad scope as **Compliance logging platform → Read**; each fine-grained scope as its own category (e.g. **Conversation messages**, **Codex logs**).

## Instructions to Connect

1. Add `chatgpt-enterprise` to `enabled_connectors` in `terraform.tfvars`, then run `terraform apply`.
2. Complete source authorization per the `TODO 1 - chatgpt-enterprise` file. A workspace Owner opens [https://admin.openai.com](https://admin.openai.com), selects the ChatGPT workspace, then **Credentials → Admin keys → Create new admin key**. Choose **Custom** permissions and grant the scopes from [Endpoints and required scopes](#endpoints-and-required-scopes) above — granting the broad `chatgpt.enterprise.compliance_export.read` scope is the simplest way to cover `/users`, `/projects`, and every `/logs` event type at once. Paste the secret as `PSOXY_CHATGPT_ENTERPRISE_ACCESS_TOKEN`. The same steps are in that TODO (generated from the [enterprise instructions template](https://github.com/Worklytics/psoxy/blob/main/infra/modules/worklytics-connector-specs/docs/chatgpt/enterprise/instructions.tftpl)).
3. Run the proxy health checks in the `TODO 2 - chatgpt-enterprise` file generated by `terraform apply`.
4. Create the **ChatGPT Enterprise via Psoxy** connection in Worklytics; see the `TODO 3 - connect chatgpt-enterprise in Worklytics.md` file generated by `terraform apply`. That file lists the values to enter in Worklytics, including:
   - **Psoxy Base URL** — your deployed proxy/function endpoint URL
   - **Bucket Name** — the async output bucket where sanitized API responses are written (this connector uses async processing)
   - **Workspace Id** — your ChatGPT Enterprise workspace id (ChatGPT Admin console, https://chatgpt.com/admin, workspace settings)
   - **Organization Id** *(optional)* — your ChatGPT organization id (ChatGPT Admin console, https://chatgpt.com/admin), distinct from the Workspace Id above; only needed to also collect cost/usage (`COSTS`) data. Leave blank to skip cost/usage collection.
5. If your Admin key does **not** have the broad `compliance_export.read` scope (or you deliberately withheld it), set this connection's fetch option `fetchProjects` to `false` — see the `/projects` behavior below. There is currently no equivalent option to skip `/users`: the people-roster sync always requests it, so a key without `compliance_export.read` will show roster-sync failures even though usage-data collection is unaffected.

`/logs` is where conversation and Codex usage land. `/projects` runs first when `fetchProjects` is left on, and the log fetch waits for that job. An Admin key granted only log categories often cannot read that stateful endpoint. Leaving the option on:

- **404:** the reader records it and continues. The projects job counts as success with zero items, the log fetch runs, and the window is marked complete. Usage data works. Project-creation items are missing, and the job log has a severe "not retryable" line. `VENDOR_EXCEPTION_NOT_FOUND` means the usage data is fine.
- **403:** that counter fails the projects job. The log fetch can still store items, but the combined fetch is a failure, so the window is not marked fetched. Later runs retry the same range, and the connection stays in a failed-fetch state. `VENDOR_ERROR-FORBIDDEN` means the window will not advance until `fetchProjects` is `false` or the key can read `/projects`.

`/users` is a separate, independently-scheduled sync (not chained to the `/logs`/`/projects` fetch window above): a key that cannot read it fails that roster sync but does not block or delay usage-data collection.

## Links

- [Admin API reference](https://chatgpt.com/public/admin/api-reference#tag/Introduction) — authorization and compliance endpoints
- [Managing Admin keys in Admin Console](https://help.openai.com/en/articles/20001407-managing-admin-keys-in-admin-console)
- [OpenAI Compliance Platform](https://help.openai.com/en/articles/9261474-openai-compliance-platform-for-enterprise-and-edu-customers)

## Examples

- [Example Rules](chatgpt-enterprise.yaml)
- Example Data : [original/sample_log_message.json](example-api-responses/original/sample_log_message.json) |
  [sanitized/sample_log_message.json](example-api-responses/sanitized/sample_log_message.json)
- Example Data (cost/usage) : [original/cost_log_message.json](example-api-responses/original/cost_log_message.json) |
  [sanitized/cost_log_message.json](example-api-responses/sanitized/cost_log_message.json)

Example API call path parameters (uppercase placeholders in generated test calls):

- `{WORKSPACE_ID}`: your ChatGPT Enterprise workspace id (Worklytics "Workspace Id" / `connector_settings.chat_gpt_enterprise_example_workspace_id`).
- `{CONVERSATION_ID}`: a conversation id from `GET /v1/compliance/workspaces/{WORKSPACE_ID}/conversations`.
- `{LOG_FILE_ID}`: a log file id from `GET /v1/compliance/workspaces/{WORKSPACE_ID}/logs` (or `GET /v1/compliance/organizations/{ORGANIZATION_ID}/logs` for `COSTS` log files).
- `{ORGANIZATION_ID}`: your ChatGPT organization id (Worklytics "Organization Id" / `connector_settings.chat_gpt_enterprise_example_organization_id`); optional, only needed for cost/usage (`COSTS`) data.

See more examples in the `docs/sources/chatgpt-enterprise/example-api-responses` folder
of the [Psoxy repository](https://github.com/Worklytics/psoxy).
