# GitLab

## Examples

- [Example Rules](gitlab.yaml)
- Example Data:
  - [original/groups.json](example-api-responses/original/groups.json) | [sanitized/groups.json](example-api-responses/sanitized/groups.json)
  - [original/issues.json](example-api-responses/original/issues.json) | [sanitized/issues.json](example-api-responses/sanitized/issues.json)
  - [original/merge_requests.json](example-api-responses/original/merge_requests.json) | [sanitized/merge_requests.json](example-api-responses/sanitized/merge_requests.json)
  - [original/users.json](example-api-responses/original/users.json) | [sanitized/users.json](example-api-responses/sanitized/users.json)

## Steps to Connect

GitLab through Psoxy uses **Project Access Tokens**, **Group Access Tokens**, or **Personal Access Tokens** for authentication.
See GitLab documentation:
- [Project Access Tokens](https://docs.gitlab.com/ee/user/project/settings/project_access_tokens.html)
- [Group Access Tokens](https://docs.gitlab.com/ee/user/group/settings/group_access_tokens.html)

### Required Scope

- `read_api`: for reading API resources (groups, projects, issues, merge requests, users, etc.)

### Setup

1. Create a Group Access Token (recommended), Project Access Token, or Personal Access Token with the `read_api` scope.
2. Update the content of `PSOXY_GITLAB_ACCESS_TOKEN` variable or `ACCESS_TOKEN` environment variable with the token value obtained in the previous step.

NOTE: derived from [worklytics-connector-specs](../../../infra/modules/worklytics-connector-specs/main.tf); refer to that for definitive information.

