# Jira Server

NOTE: derived from [worklytics-connector-specs](../../infra/modules/worklytics-connector-specs/main.tf); refer to that for definitive information.

1. Follow the instructions to create a [Personal Access Token](https://confluence.atlassian.com/enterprise/using-personal-access-tokens-1026032365.html) in your instance
2. Disable or mark a proper expiration of the token.
3. Copy the value of the token in PSOXY_JIRA_SERVER_ACCESS_TOKEN variable as part of AWS System Manager parameters store / GCP Cloud Secrets (if default implementation)
   NOTE: If your token has been created with expiration date, please remember to update it before that date to ensure connector is going to work.