# Asana

## Examples

- [Example Rules](asana.yaml)
- Example Data:
  - [original/projects.json](example-api-responses/original/projects.json) | [sanitized/projects.json](example-api-responses/sanitized/projects.json)
  - [original/tasks.json](example-api-responses/original/tasks.json) |  [sanitized/tasks.json](example-api-responses/sanitized/tasks.json)

## Steps to Connect

1. Create a [Service Account User + token](https://asana.com/guide/help/premium/service-accounts) or a sufficiently [Personal Access Token](https://developers.asana.com/docs/personal-access-token) for a sufficiently privileged user (who can see all the workspaces/teams/projects/tasks you wish  to import to Worklytics via this connection).
2. Update the content of `PSOXY_ASANA_ACCESS_TOKEN` variable or `ACCESS_TOKEN` environment variable with the token value obtained in the previous step.

NOTE: derived from [worklytics-connector-specs](../../../infra/modules/worklytics-connector-specs/main.tf); refer to that for definitive information.
