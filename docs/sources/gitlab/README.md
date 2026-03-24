# GitLab

## Examples

- [Example Rules](gitlab.yaml)
- Example Data:
  - [original/groups.json](example-api-responses/original/groups.json) | [sanitized/groups.json](example-api-responses/sanitized/groups.json)
  - [original/group_members.json](example-api-responses/original/group_members.json) | [sanitized/group_members.json](example-api-responses/sanitized/group_members.json)
  - [original/projects.json](example-api-responses/original/projects.json) | [sanitized/projects.json](example-api-responses/sanitized/projects.json)
  - [original/issues.json](example-api-responses/original/issues.json) | [sanitized/issues.json](example-api-responses/sanitized/issues.json)
  - [original/merge_requests.json](example-api-responses/original/merge_requests.json) | [sanitized/merge_requests.json](example-api-responses/sanitized/merge_requests.json)

## Steps to Connect

GitLab through Psoxy uses **Project Access Tokens** or **Group Access Tokens** for authentication.
These are long-lived tokens that provide API access without requiring user interaction.

See GitLab documentation:
- [Project Access Tokens](https://docs.gitlab.com/ee/user/project/settings/project_access_tokens.html)
- [Group Access Tokens](https://docs.gitlab.com/ee/user/group/settings/group_access_tokens.html)

The following scope is required:
- `read_api`: for reading API resources (groups, projects, issues, merge requests, users, etc.)

Maintainer role is required for fetching audit events and group members.

## Endpoints Used

| Endpoint                                                                       | Scope      | Minimum Role |
|--------------------------------------------------------------------------------|------------|--------------|
| `/api/v4/groups`                                                               | `read_api` | Reporter     |
| `/api/v4/groups/{id}/members/all`                                              | `read_api` | Maintainer   |
| `/api/v4/merge_requests/{id}`                                                  | `read_api` | Reporter     |
| `/api/v4/projects`                                                             | `read_api` | Reporter     |
| `/api/v4/projects/{id}/audit_events`                                           | `read_api` | Maintainer   |
| `/api/v4/projects/{id}/issues/{issueIid}/notes`                                | `read_api` | Reporter     |
| `/api/v4/projects/{id}/issues/{issueIid}/resource_state_events`                | `read_api` | Reporter     |
| `/api/v4/projects/{id}/merge_requests/{mergeRequestIid}/commits`               | `read_api` | Reporter     |
| `/api/v4/projects/{id}/merge_requests/{mergeRequestIid}/notes`                 | `read_api` | Reporter     |
| `/api/v4/projects/{id}/merge_requests/{mergeRequestIid}/resource_state_events` | `read_api` | Reporter     |
| `/api/v4/projects/{id}/repository/branches`                                    | `read_api` | Reporter     |
| `/api/v4/projects/{id}/repository/commits`                                     | `read_api` | Reporter     |
| `/api/v4/projects/{id}/repository/commits/{sha}`                               | `read_api` | Reporter     |
| `/api/v4/projects/{id}/repository/commits/{sha}/discussions`                   | `read_api` | Reporter     |
| `/api/v4/projects/{projectId}/issues`                                          | `read_api` | Reporter     |
| `/api/v4/projects/{projectId}/merge_requests`                                  | `read_api` | Reporter     |

- The `read_api` scope is required for all endpoints.
- "Minimum Role" is the lowest GitLab role that can access the endpoint with the required scope.

### Setup

1. Create a Group Access Token (recommended), Project Access Token, or Personal Access Token with the `read_api` scope.
2. Update the content of `PSOXY_GITLAB_ACCESS_TOKEN` variable or `ACCESS_TOKEN` environment variable with the token value obtained in the previous step.

NOTE: derived from [worklytics-connector-specs](../../../infra/modules/worklytics-connector-specs/main.tf); refer to that for definitive information.

