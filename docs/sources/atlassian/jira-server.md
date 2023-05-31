# Jira Server

NOTE: As of May 2023, Atlassian has announced they will stop supporting Jira Server on Feb 15, 2024.
Unless they choose to extend support further, our support for Jira Server as a data source will end
on that date as well.

NOTE: derived from [worklytics-connector-specs](../../infra/modules/worklytics-connector-specs/main.tf); refer to that for definitive information.

  1. Follow the instructions to create a [Personal Access Token](https://confluence.atlassian.com/enterprise/using-personal-access-tokens-1026032365.html) in your instance
  2. Disable or mark a proper expiration of the token.
  3. Copy the value of the token in PSOXY_JIRA_SERVER_ACCESS_TOKEN variable as part of AWS System
     Manager parameters store / GCP Cloud Secrets (if default implementation)
     NOTE: If your token has been created with expiration date, please remember to update it before that date to ensure connector is going to work.
