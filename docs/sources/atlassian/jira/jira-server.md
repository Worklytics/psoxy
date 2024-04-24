# Jira Server / Data Center (deprecated)

As of May 2023, Atlassian has announced they will stop supporting Jira Server on Feb 15, 2024. Our
Jira Server connector is intended to be compatible with Jira Data Center as well.

NOTE: as of Nov 2023, organizations are making production use of this connector; we've left it as
alpha due to impending obsolescence of Jira Server.

## Setup Instructions

NOTE: derived from
[worklytics-connector-specs](../../../../infra/modules/worklytics-connector-specs/main.tf); refer to
that for definitive information.

1. Follow the instructions to create a
   [Personal Access Token](https://confluence.atlassian.com/enterprise/using-personal-access-tokens-1026032365.html)
   in your instance. As this is coupled to a specific User in Jira, we recommend first creating a
   dedicated Jira user to be a "Service Account" in effect for the connection (name it
   `svc-worklytics` or something). This will give you better visibility into activity of the data
   connector as well as avoid connection inadvertently breaking if the Jira user who owns the token
   is disabled or deleted.

   That service account must have _READ_ permissions over your Jira instance, to be able to read
   issues, worklogs and comments, including their changelog where possible.

   If you're required to specify a classical scope, you can add:

   - `read:jira-work`

2. Disable or set a reasonable expiration time for the token. If you set an expiration time, it is
   **your responsibility** to re-generate the token and reset it in your host environment to
   maintain your connection.

3. Copy the value of the token in `PSOXY_JIRA_SERVER_ACCESS_TOKEN` variable as part of AWS System
   Manager Parameter Store / GCP Cloud Secrets.
