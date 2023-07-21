# Jira Server / Data Center **alpha**

As of May 2023, Atlassian has announced they will stop supporting Jira Server on Feb 15, 2024. Our
Jira Server connector is intended to be compatible with Jira Data Center as well.

NOTE: derived from [worklytics-connector-specs](../../infra/modules/worklytics-connector-specs/main.tf); refer to that for definitive information.

1. Follow the instructions to create a [Personal Access Token](https://confluence.atlassian.com/enterprise/using-personal-access-tokens-1026032365.html) in your instance.
   As this is coupled to a specific User in Jira, we recommend first creating a dedicated Jira user
   to be a "Service Account" in effect for the connection (name it `svc-worklytics` or something).
   This will give you better visibility into activity of the data connector as well as avoid
   connection inadvertently breaking if the Jira user who owns the token is disabled or deleted.

   That service account must have *READ* permissions over your Jira instance, to be able to read issues, worklogs and comments,
   including their changelog where possible.

   If you're required to specify a classical scope, you can add:
     - `read:jira-work`
2. Disable or mark a proper expiration of the token.
3. Copy the value of the token in `PSOXY_JIRA_SERVER_ACCESS_TOKEN` variable as part of AWS System
   Manager parameters store / GCP Cloud Secrets (if default implementation)
   NOTE: If your token has been created with expiration date, please remember to update it before
   that date to ensure connector is going to work.