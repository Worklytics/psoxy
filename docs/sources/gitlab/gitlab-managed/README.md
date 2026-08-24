# GitLab Managed for Self-Managed / Dedicated through Psoxy

**Connector ID:** `gitlab-managed`

**Availability:** Beta

## Examples

- [Example Rules](gitlab-managed.yaml)
- Example Data:
  - [original/groups.json](example-api-responses/original/groups.json) | [sanitized/groups.json](example-api-responses/sanitized/groups.json)
  - [original/group_members.json](example-api-responses/original/group_members.json) | [sanitized/group_members.json](example-api-responses/sanitized/group_members.json)
  - [original/projects.json](example-api-responses/original/projects.json) | [sanitized/projects.json](example-api-responses/sanitized/projects.json)
  - [original/issues.json](example-api-responses/original/issues.json) | [sanitized/issues.json](example-api-responses/sanitized/issues.json)
  - [original/merge_requests.json](example-api-responses/original/merge_requests.json) | [sanitized/merge_requests.json](example-api-responses/sanitized/merge_requests.json)

GitLab for Self-Managed / Dedicated through Psoxy uses an **Admin Access token** for authentication.

The following scope is required:
- `read_api`: for reading API resources (groups, projects, issues, merge requests, users, etc.)

## Endpoints Used

Example API call path parameters (self-descriptive names; the rules use `{id}` for several of these segments):

- `{groupId}`: a GitLab group id from `GET /api/v4/groups` (`connector_settings.gitlab_example_group_id`).
- `{userId}`: a user id from `GET /api/v4/users`.
- `{projectId}`: a GitLab project id from `GET /api/v4/projects` (`connector_settings.gitlab_example_project_id`).
- `{issueId}` / `{mergeRequestId}`: **global** issue/MR ids from project list responses (`.id`, not `.iid`).
- `{issueIid}` / `{mergeRequestIid}`: project-scoped iids from those same list responses.
- `{sha}`: a commit SHA from `GET /api/v4/projects/{projectId}/repository/commits`.

| Endpoint                                                                       |
|--------------------------------------------------------------------------------|
| `/api/v4/groups`                                                               |
| `/api/v4/version`                                                              |
| `/api/v4/groups/{groupId}/members/all`                                         |
| `/api/v4/issues/{issueId}`                                                     |
| `/api/v4/merge_requests/{mergeRequestId}`                                      |
| `/api/v4/users`                                                                |
| `/api/v4/users/{userId}/emails`                                                |
| `/api/v4/projects`                                                             |
| `/api/v4/projects/{projectId}/audit_events`                                    |
| `/api/v4/projects/{projectId}/issues/{issueIid}/notes`                         |
| `/api/v4/projects/{projectId}/issues/{issueIid}/resource_state_events`         |
| `/api/v4/projects/{projectId}/merge_requests/{mergeRequestIid}/commits`        |
| `/api/v4/projects/{projectId}/merge_requests/{mergeRequestIid}/notes`          |
| `/api/v4/projects/{projectId}/merge_requests/{mergeRequestIid}/resource_state_events` |
| `/api/v4/projects/{projectId}/repository/branches`                             |
| `/api/v4/projects/{projectId}/repository/commits`                              |
| `/api/v4/projects/{projectId}/repository/commits/{sha}`                        |
| `/api/v4/projects/{projectId}/repository/commits/{sha}/discussions`            |
| `/api/v4/projects/{projectId}/issues`                                          |
| `/api/v4/projects/{projectId}/merge_requests`                                  |


### Setup

1. We recommend to create an [admin dedicated service account](https://docs.gitlab.com/user/profile/account/create_accounts/#create-a-user-in-the-admin-area) and generate a Personal Access Token for that account with the required permissions.
2. Create the token with `read_api` scope.
2. Update the content of `PSOXY_GITLAB_MANAGED_ACCESS_TOKEN` variable or `ACCESS_TOKEN` environment variable with the token value obtained in the previous step.

NOTE: derived from [worklytics-connector-specs](../../../infra/modules/worklytics-connector-specs/main.tf); refer to that for definitive information.

