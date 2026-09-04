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

Example API call path parameters (uppercase placeholders in generated test calls; the rules use `{id}` for several of these segments):

- `{GROUP_ID}`: a GitLab group id from `GET /api/v4/groups` (`connector_settings.gitlab_example_group_id`).
- `{USER_ID}`: a user id from `GET /api/v4/users`.
- `{PROJECT_ID}`: a GitLab project id from `GET /api/v4/projects` (`connector_settings.gitlab_example_project_id`).
- `{ISSUE_ID}` / `{MERGE_REQUEST_ID}`: **global** issue/MR ids from project list responses (`.id`, not `.iid`).
- `{ISSUE_IID}` / `{MERGE_REQUEST_IID}`: project-scoped iids from those same list responses.
- `{SHA}`: a commit SHA from `GET /api/v4/projects/{PROJECT_ID}/repository/commits`.

| Endpoint                                                                       |
|--------------------------------------------------------------------------------|
| `/api/v4/groups`                                                               |
| `/api/v4/version`                                                              |
| `/api/v4/groups/{GROUP_ID}/members/all`                                         |
| `/api/v4/issues/{ISSUE_ID}`                                                     |
| `/api/v4/merge_requests/{MERGE_REQUEST_ID}`                                      |
| `/api/v4/users`                                                                |
| `/api/v4/users/{USER_ID}/emails`                                                |
| `/api/v4/projects`                                                             |
| `/api/v4/projects/{PROJECT_ID}/audit_events`                                    |
| `/api/v4/projects/{PROJECT_ID}/issues/{ISSUE_IID}/notes`                         |
| `/api/v4/projects/{PROJECT_ID}/issues/{ISSUE_IID}/resource_state_events`         |
| `/api/v4/projects/{PROJECT_ID}/merge_requests/{MERGE_REQUEST_IID}/commits`        |
| `/api/v4/projects/{PROJECT_ID}/merge_requests/{MERGE_REQUEST_IID}/notes`          |
| `/api/v4/projects/{PROJECT_ID}/merge_requests/{MERGE_REQUEST_IID}/resource_state_events` |
| `/api/v4/projects/{PROJECT_ID}/repository/branches`                             |
| `/api/v4/projects/{PROJECT_ID}/repository/commits`                              |
| `/api/v4/projects/{PROJECT_ID}/repository/commits/{SHA}`                        |
| `/api/v4/projects/{PROJECT_ID}/repository/commits/{SHA}/discussions`            |
| `/api/v4/projects/{PROJECT_ID}/issues`                                          |
| `/api/v4/projects/{PROJECT_ID}/merge_requests`                                  |


### Setup

1. We recommend to create an [admin dedicated service account](https://docs.gitlab.com/user/profile/account/create_accounts/#create-a-user-in-the-admin-area) and generate a Personal Access Token for that account with the required permissions.
2. Create the token with `read_api` scope.
2. Update the content of `PSOXY_GITLAB_MANAGED_ACCESS_TOKEN` variable or `ACCESS_TOKEN` environment variable with the token value obtained in the previous step.

NOTE: derived from [worklytics-connector-specs](../../../infra/modules/worklytics-connector-specs/main.tf); refer to that for definitive information.

