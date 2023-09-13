# Asana

## Examples

  * [Example Rules](example-rules/asana/asana.yaml)
  * Example Data : [original](api-response-examples/asana) | [sanitized](api-response-examples/asana/sanitized)

## Steps to Connect

1. Create a [Service Account User + token](https://asana.com/guide/help/premium/service-accounts)
   or a sufficiently [Personal Access Token](https://developers.asana.com/docs/personal-access-token)
   for a sufficiently privileged user (who can see all the workspaces/teams/projects/tasks you wish
   to import to Worklytics via this connection).
2. Update the content of `PSOXY_ASANA_ACCESS_TOKEN` variable or `ACCESS_TOKEN` environment variable
   with the token value obtained in the previous step.

NOTE: derived from [worklytics-connector-specs](../../infra/modules/worklytics-connector-specs/main.tf); refer to that for definitive information.




