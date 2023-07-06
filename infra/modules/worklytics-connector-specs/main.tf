
# TODO: arguably it does make sense to have these in yaml, and read them from there; bc YAML gives
# more interoperability than in a .tf file


locals {

  google_workspace_example_user  = coalesce(var.google_workspace_example_user, "REPLACE_WITH_EXAMPLE_USER@YOUR_COMPANY.COM")
  google_workspace_example_admin = coalesce(var.google_workspace_example_admin, var.google_workspace_example_user, "REPLACE_WITH_EXAMPLE_ADMIN@YOUR_COMPANY.COM")

  google_workspace_sources = {
    # GDirectory connections are a PRE-REQ for gmail, gdrive, and gcal connections. remove only
    # if you plan to directly connect Directory to worklytics (without proxy). such a scenario is
    # used for customers who care primarily about pseudonymizing PII of external subjects with whom
    # they collaborate in GMail/GCal/GDrive. the Directory does not contain PII of subjects external
    # to the Google Workspace, so may be directly connected in such scenarios.
    "gdirectory" : {
      source_kind : "gdirectory",
      worklytics_connector_id : "gdirectory-psoxy",
      display_name : "Google Directory"
      identifier_scope_id : "gapps"
      apis_consumed : [
        "admin.googleapis.com"
      ]
      oauth_scopes_needed : [
        "https://www.googleapis.com/auth/admin.directory.user.readonly",
        "https://www.googleapis.com/auth/admin.directory.user.alias.readonly",
        "https://www.googleapis.com/auth/admin.directory.domain.readonly",
        "https://www.googleapis.com/auth/admin.directory.group.readonly",
        "https://www.googleapis.com/auth/admin.directory.group.member.readonly",
        "https://www.googleapis.com/auth/admin.directory.orgunit.readonly",
        "https://www.googleapis.com/auth/admin.directory.rolemanagement.readonly"
      ]
      source_auth_strategy : "gcp_service_account_key"
      target_host : "admin.googleapis.com"
      environment_variables : {}
      example_api_calls : [
        "/admin/directory/v1/users?customer=my_customer&maxResults=10",
        "/admin/directory/v1/groups?customer=my_customer&maxResults=10",
        "/admin/directory/v1/customer/my_customer/domains",
        "/admin/directory/v1/customer/my_customer/roles?maxResults=10"
      ]
      example_api_calls_user_to_impersonate : local.google_workspace_example_admin
    },
    "gcal" : {
      source_kind : "gcal",
      worklytics_connector_id : "gcal-psoxy",
      display_name : "Google Calendar"
      identifier_scope_id : "gapps"
      apis_consumed : [
        "calendar-json.googleapis.com"
      ]
      source_auth_strategy : "gcp_service_account_key"
      target_host : "www.googleapis.com"
      oauth_scopes_needed : [
        "https://www.googleapis.com/auth/calendar.readonly"
      ],
      environment_variables : {}
      example_api_calls : [
        "/calendar/v3/calendars/primary",
        "/calendar/v3/users/me/settings",
        "/calendar/v3/calendars/primary/events?maxResults=10"
      ]
      example_api_calls_user_to_impersonate : local.google_workspace_example_user
    },
    "gmail" : {
      source_kind : "gmail",
      worklytics_connector_id : "gmail-meta-psoxy",
      display_name : "GMail"
      identifier_scope_id : "gapps"
      apis_consumed : [
        "gmail.googleapis.com"
      ]
      source_auth_strategy : "gcp_service_account_key"
      target_host : "www.googleapis.com"
      oauth_scopes_needed : [
        "https://www.googleapis.com/auth/gmail.metadata"
      ],
      environment_variables : {},
      example_api_calls : [
        "/gmail/v1/users/me/messages?maxResults=10"
      ]
      example_api_calls_user_to_impersonate : local.google_workspace_example_user
    },
    "google-chat" : {
      source_kind : "google-chat",
      worklytics_connector_id : "google-chat-psoxy",
      display_name : "Google Chat"
      identifier_scope_id : "gapps"
      apis_consumed : [
        "admin.googleapis.com"
      ]
      source_auth_strategy : "gcp_service_account_key"
      target_host : "admin.googleapis.com"
      oauth_scopes_needed : [
        "https://www.googleapis.com/auth/admin.reports.audit.readonly"
      ]
      environment_variables : {}
      example_api_calls : [
        "/admin/reports/v1/activity/users/all/applications/chat?maxResults=10"
      ]
      example_api_calls_user_to_impersonate : local.google_workspace_example_admin
    },
    "google-meet" : {
      source_kind : "google-meet"
      worklytics_connector_id : "google-meet-psoxy"
      display_name : "Google Meet"
      identifier_scope_id : "gapps"
      apis_consumed : [
        "admin.googleapis.com"
      ]
      source_auth_strategy : "gcp_service_account_key"
      target_host : "admin.googleapis.com"
      oauth_scopes_needed : [
        "https://www.googleapis.com/auth/admin.reports.audit.readonly"
      ]
      environment_variables : {}
      example_api_calls : [
        "/admin/reports/v1/activity/users/all/applications/meet?maxResults=10"
      ]
      example_api_calls_user_to_impersonate : local.google_workspace_example_admin
    },
    "gdrive" : {
      source_kind : "gdrive",
      worklytics_connector_id : "gdrive-psoxy",
      display_name : "Google Drive"
      identifier_scope_id : "gapps"
      apis_consumed : [
        "drive.googleapis.com"
      ]
      source_auth_strategy : "gcp_service_account_key"
      target_host : "www.googleapis.com"
      oauth_scopes_needed : [
        "https://www.googleapis.com/auth/drive.metadata.readonly"
      ],
      environment_variables : {}
      example_api_calls : [
        "/drive/v2/files",
        "/drive/v3/files"
      ],
      example_api_calls_user_to_impersonate : local.google_workspace_example_user
    }
  }


  jira_cloud_id         = coalesce(var.jira_cloud_id, "YOUR_JIRA_CLOUD_ID")
  example_jira_issue_id = coalesce(var.example_jira_issue_id, "YOUR_JIRA_EXAMPLE_ISSUE_ID")

  # Microsoft 365 sources; add/remove as you wish
  # See https://docs.microsoft.com/en-us/graph/permissions-reference for all the permissions available in AAD Graph API
  msft_365_connectors = {
    "azure-ad" : {
      worklytics_connector_id : "azure-ad-psoxy",
      source_kind : "azure-ad",
      display_name : "Azure Directory"
      identifier_scope_id : "azure-ad"
      source_auth_strategy : "oauth2_refresh_token"
      target_host : "graph.microsoft.com"
      required_oauth2_permission_scopes : [],
      # Delegated permissions (from `az ad sp list --query "[?appDisplayName=='Microsoft Graph'].oauth2Permissions" --all`)
      required_app_roles : [
        # Application permissions (form az ad sp list --query "[?appDisplayName=='Microsoft Graph'].appRoles" --all
        "User.Read.All",
        "Group.Read.All"
      ]
      environment_variables : {
        GRANT_TYPE : "workload_identity_federation" # by default, assumed to be of type 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer'
        TOKEN_SCOPE : "https://graph.microsoft.com/.default"
        REFRESH_ENDPOINT = "https://login.microsoftonline.com/${var.msft_tenant_id}/oauth2/v2.0/token"
      },
      example_api_calls : [
        "/v1.0/users",
        "/v1.0/users/${var.example_msft_user_guid}",
        "/v1.0/groups",
        "/v1.0/groups/{group-id}/members"
      ]
    },
    "outlook-cal" : {
      source_kind : "outlook-cal",
      worklytics_connector_id : "outlook-cal-psoxy",
      display_name : "Outlook Calendar"
      identifier_scope_id : "azure-ad"
      source_auth_strategy : "oauth2_refresh_token"
      target_host : "graph.microsoft.com"
      required_oauth2_permission_scopes : [],
      required_app_roles : [
        "OnlineMeetings.Read.All",
        "Calendars.Read",
        "MailboxSettings.Read",
        "Group.Read.All",
        "User.Read.All"
      ],
      environment_variables : {
        GRANT_TYPE : "workload_identity_federation" # by default, assumed to be of type 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer'
        TOKEN_SCOPE : "https://graph.microsoft.com/.default"
        REFRESH_ENDPOINT = "https://login.microsoftonline.com/${var.msft_tenant_id}/oauth2/v2.0/token"
      },
      example_api_calls : [
        "/v1.0/users",
        "/v1.0/users/${var.example_msft_user_guid}/events",
        "/v1.0/users/${var.example_msft_user_guid}/calendarView?startDateTime=2022-10-01T00:00:00Z&endDateTime=${timestamp()}",
        "/v1.0/users/${var.example_msft_user_guid}/mailboxSettings",
        "/v1.0/groups",
        "/v1.0/groups/{group-id}/members"
      ]
    },
    "outlook-mail" : {
      source_kind : "outlook-mail"
      worklytics_connector_id : "outlook-mail-psoxy",
      display_name : "Outlook Mail"
      identifier_scope_id : "azure-ad"
      source_auth_strategy : "oauth2_refresh_token"
      target_host : "graph.microsoft.com"
      required_oauth2_permission_scopes : [],
      required_app_roles : [
        "Mail.ReadBasic.All",
        "MailboxSettings.Read",
        "Group.Read.All",
        "User.Read.All"
      ],
      environment_variables : {
        GRANT_TYPE : "workload_identity_federation"
        # by default, assumed to be of type 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer'
        TOKEN_SCOPE : "https://graph.microsoft.com/.default"
        REFRESH_ENDPOINT : "https://login.microsoftonline.com/${var.msft_tenant_id}/oauth2/v2.0/token"
      }
      example_api_calls : [
        "/beta/users",
        "/beta/users/${var.example_msft_user_guid}/mailboxSettings",
        "/beta/users/${var.example_msft_user_guid}/mailFolders/SentItems/messages",
        "/v1.0/groups",
        "/v1.0/groups/{group-id}/members"
      ]
    }
  }
  oauth_long_access_connectors = {
    asana = {
      source_kind : "asana",
      worklytics_connector_id : "asana-psoxy"
      display_name : "Asana"
      identifier_scope_id : "asana"
      worklytics_connector_name : "Asana via Psoxy"
      target_host : "app.asana.com"
      source_auth_strategy : "oauth2_access_token"
      environment_variables : {}
      secured_variables : [
        { name : "ACCESS_TOKEN", writable : false },
      ]
      reserved_concurrent_executions : null
      example_api_calls_user_to_impersonate : null
      example_api_calls : [
        "/api/1.0/workspaces",
        "/api/1.0/users?workspace={ANY_WORKSPACE_GID}&limit=10",
        "/api/1.0/workspaces/{ANY_WORKSPACE_GID}/teams&limit=10",
        "/api/1.0/teams/{ANY_TEAM_GID}/projects?limit=20",
        "/api/1.0/tasks?project={ANY_PROJECT_GID}",
        "/api/1.0/tasks/{ANY_TASK_GID}",
        "/api/1.0/tasks/{ANY_TASK_GID}/stories",
      ]
      external_token_todo : <<EOT
  1. Create a [Service Account User + token](https://asana.com/guide/help/premium/service-accounts)
    or a sufficiently [Personal Access Token](https://developers.asana.com/docs/personal-access-token)
    for a sufficiently privileged user (who can see all the workspaces/teams/projects/tasks you wish to
    import to Worklytics via this connection).
  2. Update the content of PSOXY_ASANA_ACCESS_TOKEN variable with the previous token value obtained
EOT
    }
    salesforce = {
      source_kind : "salesforce",
      worklytics_connector_id : "salesforce-psoxy"
      display_name : "Salesforce"
      identifier_scope_id : "salesforce"
      worklytics_connector_name : "Salesforce via Psoxy"
      target_host : var.salesforce_domain
      source_auth_strategy : "oauth2_refresh_token"
      environment_variables : {
        GRANT_TYPE : "client_credentials"
        CREDENTIALS_FLOW : "client_secret"
        REFRESH_ENDPOINT : "https://${var.salesforce_domain}/services/oauth2/token"
      }
      secured_variables : [
        { name : "CLIENT_SECRET", writable : false },
        { name : "CLIENT_ID", writable : false },
      ]
      reserved_concurrent_executions : null
      example_api_calls_user_to_impersonate : null
      example_api_calls : [
        "/services/data/v57.0/sobjects/Account/describe",
        "/services/data/v57.0/sobjects/ActivityHistory/describe",
        "/services/data/v57.0/sobjects/Account/updated?start=2016-03-09T18%3A44%3A00%2B00%3A00&end=2023-03-09T18%3A44%3A00%2B00%3A00",
        "/services/data/v57.0/composite/sobjects/User?ids={ANY_USER_ID}&fields=Alias,AccountId,ContactId,CreatedDate,CreatedById,Email,EmailEncodingKey,Id,IsActive,LastLoginDate,LastModifiedDate,ManagerId,Name,TimeZoneSidKey,Username,UserRoleId,UserType",
        "/services/data/v57.0/composite/sobjects/Account?ids={ANY_ACCOUNT_ID}&fields=Id,AnnualRevenue,CreatedDate,CreatedById,IsDeleted,LastActivityDate,LastModifiedDate,LastModifiedById,NumberOfEmployees,OwnerId,Ownership,ParentId,Rating,Sic,Type",
        "/services/data/v57.0/query?q=SELECT%20%28SELECT%20AccountId%2CActivityDate%2CActivityDateTime%2CActivitySubtype%2CActivityType%2CCallDurationInSeconds%2CCallType%2CCreatedDate%2CCreatedById%2CDurationInMinutes%2CEndDateTime%2CId%2CIsAllDayEvent%2CIsDeleted%2CIsHighPriority%2CIsTask%2CLastModifiedDate%2CLastModifiedById%2COwnerId%2CPriority%2CStartDateTime%2CStatus%2CWhatId%2CWhoId%20FROM%20ActivityHistories%20ORDER%20BY%20LastModifiedDate%20DESC%20NULLS%20LAST%29%20FROM%20Account%20where%20id%3D%27{ANY_ACCOUNT_ID}%27",
        "/services/data/v57.0/query?q=SELECT%20Id%20FROM%20Account%20ORDER%20BY%20Id%20ASC",
        "/services/data/v57.0/query?q=SELECT%20Id%20FROM%20User%20ORDER%20BY%20Id%20ASC",
        "/services/data/v57.0/query?q=SELECT+Alias,AccountId,ContactId,CreatedDate,CreatedById,Email,EmailEncodingKey,Id,IsActive,LastLoginDate,LastModifiedDate,ManagerId,Name,TimeZoneSidKey,Username,UserRoleId,UserType+FROM+User+WHERE+LastModifiedDate+%3E%3D+2016-01-01T00%3A00%3A00Z+AND+LastModifiedDate+%3C+2023-03-01T00%3A00%3A00Z+ORDER+BY+LastModifiedDate+DESC+NULLS+LAST",
        "/services/data/v57.0/query?q=SELECT+Id,AnnualRevenue,CreatedDate,CreatedById,IsDeleted,LastActivityDate,LastModifiedDate,LastModifiedById,NumberOfEmployees,OwnerId,Ownership,ParentId,Rating,Sic,Type+FROM+Account+WHERE+LastModifiedDate+%3E%3D+2016-01-01T00%3A00%3A00Z+AND+LastModifiedDate+%3C+2023-03-01T00%3A00%3A00Z+ORDER+BY+LastModifiedDate+DESC+NULLS+LAST"
      ]
      external_token_todo : <<EOT
  1. Create a [Salesforce application + client credentials flow](https://help.salesforce.com/s/articleView?language=en_US&id=sf.remoteaccess_oauth_client_credentials_flow.htm&type=5)
    with following permissions:
    - Manage user data via APIs (`api`)
    - Access Connect REST API resources (`chatter_api`)
    - Perform requests at any time (`refresh_token`, `offline_access`)
    - Access unique user identifiers (`openid`)
    - Access Lightning applications (`lightning`)
    - Access content resources (`content`)
    - Perform ANSI SQL queries on Customer Data Platform data (`cdp_query_api`)

  Apart from Salesforce instructions please review the following:
  - "Callback URL" can be anything, not required in this flow but required by Salesforce.
  - Application is marked with "Enable Client Credentials Flow"
  - You have to assign an user for Client Credentials, be sure:
      - A "run as" user marked with "API Only Permission" needs to be associated
      - The policy associated to the user have the enabled next Administrative Permissions:
        - API Enabled
        - APEX REST Services
      - And the policy has the application created marked as enabled in "Connected App Access". Otherwise requests will return 401 with INVALID_SESSION_ID
  2. Once created, open "Manage Consumer Details"
  3. Update the content of `PSOXY_SALESFORCE_CLIENT_ID` from Consumer Key	and `PSOXY_SALESFORCE_CLIENT_SECRET` from Consumer Secret
EOT
    }
    slack-discovery-api = {
      source_kind : "slack"
      identifier_scope_id : "slack"
      worklytics_connector_id : "slack-discovery-api-psoxy",
      worklytics_connector_name : "Slack via Psoxy",
      display_name : "Slack Discovery API"
      target_host : "www.slack.com"
      source_auth_strategy : "oauth2_access_token"
      oauth_scopes_needed : [
        "discovery:read",
      ]
      environment_variables : {}
      secured_variables : [
        { name : "ACCESS_TOKEN", writable : false },
      ]
      reserved_concurrent_executions : null
      example_api_calls_user_to_impersonate : null
      example_api_calls : [
        "/api/discovery.enterprise.info",
        "/api/discovery.conversations.list",
        "/api/discovery.conversations.history?channel={CHANNEL_ID}&limit=10",
        "/api/discovery.users.list",
      ]
      external_token_todo : <<EOT
### Slack Discovery Setup

For enabling Slack Discovery with the Psoxy you must first set up an app on your Slack Enterprise
instance.

1. Go to https://api.slack.com/apps and create an app.
   - Select "From scratch", choose a name (for example "Worklytics connector") and a development workspace
2. Take note of your App ID (listed in "App Credentials"), contact your Slack representative and ask
   them to enable `discovery:read` scope for that App ID.
   If they also enable `discovery:write` then delete it for safety, the app just needs read access.

The next step depends on your installation approach you might need to change slightly

#### Org wide install

Use this step if you want to install in the whole org, across multiple workspaces.

1. Add a bot scope (not really used, but Slack doesn't allow org-wide installations without a bot scope).
   The app won't use it at all. Just add for example the `users:read` scope, read-only.
2. Under "Settings > Manage Distribution > Enable Org-Wide App installation",
   click on "Opt into Org Level Apps", agree and continue. This allows to distribute the app internally
   on your organization, to be clear it has nothing to do with public distribution or Slack app directory.
3. Generate the following URL replacing the placeholder for *YOUR_CLIENT_ID* and save it for
   later:
4. Go to "OAuth & Permissions" and add the previous URL as "Redirect URLs"
5. Go to "Settings > Install App", and choose "Install to Organization". A Slack admin should grant
   the app the permissions and the app will be installed.
6. Copy the "User OAuth Token" (also listed under "OAuth & Permissions") and store as
   `PSOXY_SLACK_DISCOVERY_API_ACCESS_TOKEN` in the psoxy's Secret
   Manager. Otherwise, share the token with the AWS/GCP administrator completing the implementation.

#### Workspace install

Use this steps if you intend to install in just one workspace within your org.

1. Go to "Settings > Install App", click on "Install into *workspace*"
2. Copy the "User OAuth Token" (also listed under "OAuth & Permissions") and store as
   `PSOXY_SLACK_DISCOVERY_API_ACCESS_TOKEN` in the psoxy's Secret
   Manager. Otherwise, share the token with the AWS/GCP administrator completing the implementation.
EOT
    }
    zoom = {
      source_kind : "zoom"
      worklytics_connector_id : "zoom-psoxy"
      display_name : "Zoom"
      worklytics_connector_name : "Zoom via Psoxy"
      identifier_scope_id : "zoom"
      source_auth_strategy : "oauth2_refresh_token"
      target_host : "api.zoom.us"
      environment_variables : {
        GRANT_TYPE : "account_credentials"
        REFRESH_ENDPOINT : "https://zoom.us/oauth/token"
        USE_SHARED_TOKEN : "TRUE"
      }
      secured_variables : [
        { name : "CLIENT_SECRET", writable : false },
        { name : "CLIENT_ID", writable : false },
        { name : "ACCOUNT_ID", writable : false },
        { name : "ACCESS_TOKEN", writable : true },
        { name : "OAUTH_REFRESH_TOKEN", writable : true, lockable : true }, # q: needed? per logic as of 9 June 2023, would be created
      ],
      reserved_concurrent_executions : null # 1
      example_api_calls_user_to_impersonate : null
      example_api_calls : [
        "/v2/users",
        "/v2/users/{USER_ID}/meetings",
        "/v2/past_meetings/{MEETING_ID}",
        "/v2/past_meetings/{MEETING_ID}/instances",
        "/v2/past_meetings/{MEETING_ID}/participants",
        "/v2/report/users/{userId}/meetings",
        "/v2/report/meetings/{meetingId}",
        "/v2/report/meetings/{meetingId}/participants"
      ],
      external_token_todo : <<EOT
## Zoom Setup
Zoom connector through Psoxy requires a custom managed app on the Zoom Marketplace (in development
mode, no need to publish).
1. Go to https://marketplace.zoom.us/develop/create and create an app of type "Server to Server OAuth"
2. After creation it will show the App Credentials. Share them with the AWS/GCP administrator, the
following secret values must be filled in the Secret Manager for the Proxy with the appropriate values:
- `PSOXY_ZOOM_CLIENT_ID`
- `PSOXY_ZOOM_ACCOUNT_ID`
- `PSOXY_ZOOM_CLIENT_SECRET`
Anytime the *client secret* is regenerated it needs to be updated in the Proxy too.
3. Fill the information section
4. Fill the scopes section, enabling the following:
- Users / View all user information /user:read:admin
  - To be able to gather information about the zoom users
- Meetings / View all user meetings /meeting:read:admin
  - Allows us to list all user meeting
- Report / View report data /report:read:admin
  - Last 6 months view for user meetings
5. Activate the app
EOT
    },
    dropbox-business = {
      source_kind : "dropbox-business"
      worklytics_connector_id : "dropbox-business-log-psoxy"
      target_host : "api.dropboxapi.com"
      source_auth_strategy : "oauth2_refresh_token"
      display_name : "Dropbox Business"
      identifier_scope_id : "dropbox-business"
      worklytics_connector_name : "Dropbox Business via Psoxy"
      secured_variables : [
        { name : "REFRESH_TOKEN", writable : false },
        { name : "CLIENT_ID", writable : false },
        { name : "CLIENT_SECRET", writable : false },
      ],
      environment_variables : {
        GRANT_TYPE : "refresh_token"
        REFRESH_ENDPOINT : "https://api.dropboxapi.com/oauth2/token"
      }
      reserved_concurrent_executions : null
      example_api_calls_user_to_impersonate : null
      example_api_calls : [
        "/2/team/members/list_v2",
        "/2/team/groups/list",
        "/2/team_log/get_events",
      ],
      external_token_todo : <<EOT
Dropbox connector through Psoxy requires a Dropbox Application created in Dropbox Console. The application
does not require to be public, and it needs to have the following scopes to support
all the operations for the connector:

- members.read: member listing
- events.read: event listing
- groups.read: group listing

1. Go to https://www.dropbox.com/apps and Build an App
2. Then go https://www.dropbox.com/developers to enter in `App Console` to configure your app
3. Now you are in the app, go to `Permissions` and mark all the scopes described before. NOTE: Probably in the UI will mark you more required permissions automatically (like *account_info_read*.) Just mark the ones
   described and the UI will ask you to include required.
4. On settings, you could access to `App key` and `App secret`. You can create an access token here, but with limited
   expiration. We need to create a long-lived token, so edit the following URL with your `App key` and paste it into the
   browser:

   `https://www.dropbox.com/oauth2/authorize?client_id=<APP_KEY>&token_access_type=offline&response_type=code`

   That will return an `Authorization Code` that you have to paste.
   **NOTE** This `Authorization Code` if for a one single use; if expired or used you will need to get it again pasting
   the
   URL in the browser.
5. Now, replace the values in following URL and run it from command line in your terminal. Replace `Authorization Code`
   , `App key`
   and `App secret` in the placeholders:

   `curl https://api.dropbox.com/oauth2/token -d code=<AUTHORIZATION_CODE> -d grant_type=authorization_code -u <APP_KEY>:<APP_SECRET>`
6. After running that command, if successful you will see a [JSON response](https://www.dropbox.com/developers/documentation/http/documentation#oauth2-authorize) like this:

```json
{
  "access_token": "some short live access token",
  "token_type": "bearer",
  "expires_in": 14399,
  "refresh_token": "some long live token we are going to use",
  "scope": "account_info.read events.read files.metadata.read groups.read members.read team_data.governance.read team_data.governance.write team_data.member",
  "uid": "",
  "team_id": "some team id"
}
```
7. Finally set following variables in AWS System Manager parameters store / GCP Cloud Secrets (if default implementation):
  - `PSOXY_DROPBOX_BUSINESS_REFRESH_TOKEN` secret variable with value of `refresh_token` received in previous response
  - `PSOXY_DROPBOX_BUSINESS_CLIENT_ID` with `App key` value.
  - `PSOXY_DROPBOX_BUSINESS_CLIENT_SECRET` with `App secret` value.

EOT
    },
    jira-server = {
      source_kind : "jira-server"
      worklytics_connector_id : "jira-server-psoxy"
      target_host : var.jira_server_url
      source_auth_strategy : "oauth2_access_token"
      display_name : "Jira Server REST API"
      identifier_scope_id : "jira"
      worklytics_connector_name : "Jira Server REST API via Psoxy"
      secured_variables : [
        { name : "ACCESS_TOKEN", writable : false },
      ],
      reserved_concurrent_executions : null
      example_api_calls_user_to_impersonate : null
      example_api_calls : [
        "/rest/api/2/search?maxResults=25",
        "/rest/api/2/issue/${local.example_jira_issue_id}/comment?maxResults=25",
        "/rest/api/2/issue/${local.example_jira_issue_id}/worklog?maxResults=25",
        "/rest/api/latest/search?maxResults=25",
        "/rest/api/latest/issue/${local.example_jira_issue_id}/comment?maxResults=25",
        "/rest/api/latest/issue/${local.example_jira_issue_id}/worklog?maxResults=25",
      ],
      external_token_todo : <<EOT
  1. Follow the instructions to create a [Personal Access Token](https://confluence.atlassian.com/enterprise/using-personal-access-tokens-1026032365.html) in your instance.
     As this is coupled to a specific User in Jira, we recommend first creating a dedicated Jira user
     to be a "Service Account" in effect for the connection (name it `svc-worklytics` or something).
     This will give you better visibility into activity of the data connector as well as avoid
     connection inadvertently breaking if the Jira user who owns the token is disabled or deleted.
  2. Disable or mark a proper expiration of the token.
  3. Copy the value of the token in `PSOXY_JIRA_SERVER_ACCESS_TOKEN` variable as part of AWS System
     Manager parameters store / GCP Cloud Secrets (if default implementation)
     NOTE: If your token has been created with expiration date, please remember to update it before
     that date to ensure connector is going to work.
EOT
    }
    jira-cloud = {
      source_kind : "jira-cloud"
      worklytics_connector_id : "jira-cloud-psoxy"
      target_host : "api.atlassian.com"
      source_auth_strategy : "oauth2_refresh_token"
      display_name : "Jira REST API"
      identifier_scope_id : "jira"
      worklytics_connector_name : "Jira REST API via Psoxy"
      secured_variables : [
        { name : "ACCESS_TOKEN", writable : true },
        { name : "REFRESH_TOKEN", writable : true },
        { name : "OAUTH_REFRESH_TOKEN", writable : true, lockable : true },
        { name : "CLIENT_ID", writable : false },
        { name : "CLIENT_SECRET", writable : false }
      ],
      environment_variables : {
        GRANT_TYPE : "refresh_token"
        REFRESH_ENDPOINT : "https://auth.atlassian.com/oauth/token"
        USE_SHARED_TOKEN : "TRUE"
      }
      settings_to_provide = {
        "Jira Cloud Id" = local.jira_cloud_id
      }
      reserved_concurrent_executions : null
      example_api_calls_user_to_impersonate : null
      example_api_calls : [
        "/oauth/token/accessible-resources", # obtain Jira Cloud ID from here
        "/ex/jira/${local.jira_cloud_id}/rest/api/2/users",
        "/ex/jira/${local.jira_cloud_id}/rest/api/2/users",
        "/ex/jira/${local.jira_cloud_id}/rest/api/2/group/bulk",
        "/ex/jira/${local.jira_cloud_id}/rest/api/2/search?maxResults=25",
        "/ex/jira/${local.jira_cloud_id}/rest/api/2/issue/${local.example_jira_issue_id}/changelog?maxResults=25",
        "/ex/jira/${local.jira_cloud_id}/rest/api/2/issue/${local.example_jira_issue_id}/comment?maxResults=25",
        "/ex/jira/${local.jira_cloud_id}/rest/api/2/issue/${local.example_jira_issue_id}/worklog?maxResults=25",
        "/ex/jira/${local.jira_cloud_id}/rest/api/3/users",
        "/ex/jira/${local.jira_cloud_id}/rest/api/3/group/bulk",
        "/ex/jira/${local.jira_cloud_id}/rest/api/3/search?maxResults=25",
        "/ex/jira/${local.jira_cloud_id}/rest/api/3/issue/${local.example_jira_issue_id}/changelog?maxResults=25",
        "/ex/jira/${local.jira_cloud_id}/rest/api/3/issue/${local.example_jira_issue_id}/comment?maxResults=25",
        "/ex/jira/${local.jira_cloud_id}/rest/api/3/issue/${local.example_jira_issue_id}/worklog?maxResults=25",
        "/ex/jira/${local.jira_cloud_id}/rest/api/3/project/search?maxResults=25",
      ],
      external_token_todo : <<EOT
## Prerequisites
Jira OAuth 2.0 (3LO) through Psoxy requires a Jira Cloud account with following classical scopes:

- read:jira-user: for getting generic user information
- read:jira-work: for getting information about issues, comments, etc

And following granular scopes:
- read:account: for getting user emails
- read:group:jira: for retrieving group members
- read:avatar:jira: for retrieving group members

## Setup Instructions
  1. Go to https://developer.atlassian.com/console/myapps/ and click on "Create"

  2. Then click "Authorize" and "Add", adding `http://localhost` as callback URI. It can be any URL
     that matches the settings.

  3. Now navigate to "Permissions" and click on "Add" for Jira. Once added, click on "Configure".
     Add following scopes as part of "Classic Scopes":
       - `read:jira-user`
       - `read:jira-work`
     And these from "Granular Scopes":
       - `read:group:jira`
       - `read:avatar:jira`
       - `read:user:jira`
     Then repeat the same but for "User Identity API", adding the following scope:
       - `read:account`

  4. Once Configured, go to "Settings" and copy the "Client Id" and "Secret". You will use these to
     obtain an OAuth `refresh_token`.

  5. Build an OAuth authorization endpoint URL by copying the value for "Client Id" obtained in the
    previous step into the URL below. Then open the result in a web browser:

   `https://auth.atlassian.com/authorize?audience=api.atlassian.com&client_id=<CLIENT ID>&scope=offline_access%20read:group:jira%20read:avatar:jira%20read:user:jira%20read:account%20read:jira-user%20read:jira-work&redirect_uri=http://localhost&state=YOUR_USER_BOUND_VALUE&response_type=code&prompt=consent`

  6. Choose a site in your Jira workspace to allow access for this application and click "Accept".
     As the callback does not exist, you will see an error. But in the URL of your browser you will see
     something like this as URL:

    `http://localhost/?state=YOUR_USER_BOUND_VALUE&code=eyJhbGc...`

     Copy the value of the `code` parameter from that URI. It is the "authorization code" required
     for next step.

     **NOTE** This "Authorization Code" is single-use; if it expires or is used, you will need to obtain
     a new code by  again pasting the authorization URL in the browser.

  7. Now, replace the values in following URL and run it from command line in your terminal. Replace `YOUR_AUTHENTICATION_CODE`, `YOUR_CLIENT_ID` and `YOUR_CLIENT_SECRET` in the placeholders:

    `curl --request POST --url 'https://auth.atlassian.com/oauth/token' --header 'Content-Type: application/json' --data '{"grant_type": "authorization_code","client_id": "YOUR_CLIENT_ID","client_secret": "YOUR_CLIENT_SECRET", "code": "YOUR_AUTHENTICATION_CODE", "redirect_uri": "http://localhost"}'`

  8. After running that command, if successful you will see a [JSON response](https://developer.atlassian.com/cloud/jira/platform/oauth-2-3lo-apps/#2--exchange-authorization-code-for-access-token) like this:

```json
{
  "access_token": "some short live access token",
  "expires_in": 3600,
  "token_type": "Bearer",
  "refresh_token": "some long live token we are going to use",
  "scope": "read:jira-work offline_access read:jira-user"
}
```

9. Set the following variables in AWS System Manager parameters store / GCP Cloud Secrets (if default implementation):
     - `PSOXY_JIRA_CLOUD_ACCESS_TOKEN` secret variable with value of `access_token` received in previous response
     - `PSOXY_JIRA_CLOUD_REFRESH_TOKEN` secret variable with value of `refresh_token` received in previous response
     - `PSOXY_JIRA_CLOUD_CLIENT_ID` with `Client Id` value.
     - `PSOXY_JIRA_CLOUD_CLIENT_SECRET` with `Client Secret` value.

 10. Optional, obtain the "Cloud ID" of your Jira instance. Use the following command, with the
    `access_token` obtained in the previous step in place of `<ACCESS_TOKEN>` below:

   `curl --header 'Authorization: Bearer <ACCESS_TOKEN>' --url 'https://api.atlassian.com/oauth/token/accessible-resources'`

   And its response will be something like:

```json
[
  {
    "id":"SOME UUID",
    "url":"https://your-site.atlassian.net",
    "name":"your-site-name",
    "scopes":["read:jira-user","read:jira-work"],
    "avatarUrl":"https://site-admin-avatar-cdn.prod.public.atl-paas.net/avatars/240/rocket.png"
  }
]
```

  Add the `id` value from that JSON response as the value of the `jira_cloud_id` variable in the
  `terraform.tfvars` file of your Terraform configuration. This will generate all the test URLs with
  a proper value.
EOT
    }
  }

  bulk_connectors = {
    "badge" = {
      source_kind               = "badge"
      worklytics_connector_id   = "bulk-import-psoxy",
      worklytics_connector_name = "Bulk Data Import via Psoxy"
      rules = {
        columnsToRedact = []
        columnsToPseudonymize = [
          "EMPLOYEE_ID", # primary key
          # "employee_email", # if exists
        ]
      }
      settings_to_provide = {
        "Data Source Processing" = "badge"
      }
      example_file = "docs/sources/bulk/badge-example.csv"
    }
    "hris" = {
      source_kind               = "hris"
      worklytics_connector_id   = "hris-import-psoxy"
      worklytics_connector_name = "HRIS Data Import via Psoxy"
      rules = {
        columnsToRedact = []
        columnsToPseudonymize = [
          "EMPLOYEE_ID",    # primary key
          "EMPLOYEE_EMAIL", # for matching
          "MANAGER_ID",     # should match to employee_id
          # "MANAGER_EMAIL"      # if exists
        ]
      }
      settings_to_provide = {
        "Parser" = "EMPLOYEE_SNAPSHOT"
      }
      example_file = "docs/sources/bulk/hris-example.csv"
    }
    "survey" = {
      worklytics_connector_id   = "survey-import-psoxy"
      source_kind               = "survey"
      worklytics_connector_name = "Survey Data Import via Psoxy"
      rules = {
        columnsToRedact = []
        columnsToPseudonymize = [
          "EMPLOYEE_ID", # primary key
          # "EMPLOYEE_EMAIL", # if exists
        ]
      }
      example_file = "docs/sources/bulk/survey-example.csv"
    }
    "qualtrics" = {
      source_kind               = "qualtrics"
      worklytics_connector_id   = "survey-import-psoxy"
      worklytics_connector_name = "Survey Data Import via Psoxy"
      rules = {
        columnsToRedact = []
        columnsToPseudonymize = [
          "EMPLOYEE_ID", # primary key
          # "employee_email", # if exists
        ]
      }
      example_file = "docs/sources/bulk/survey-example.csv"
    }
  }

  # to expose via console
  # eg, `echo "local.available_connector_ids" | terraform console` will print this
  available_connector_ids = keys(merge(
    local.google_workspace_sources,
    local.msft_365_connectors,
    local.oauth_long_access_connectors,
    local.bulk_connectors,
  ))
}

# computed values filtered by enabled connectors
locals {
  enabled_google_workspace_connectors = {
    for k, v in local.google_workspace_sources : k => v if contains(var.enabled_connectors, k)
  }
  enabled_msft_365_connectors = {
    for k, v in local.msft_365_connectors : k => v if contains(var.enabled_connectors, k) && length(try(var.msft_tenant_id, "")) > 0
  }
  enabled_oauth_long_access_connectors = { for k, v in local.oauth_long_access_connectors : k => v if contains(var.enabled_connectors, k) }

  enabled_oauth_long_access_connectors_todos = { for k, v in local.enabled_oauth_long_access_connectors : k => v if v.external_token_todo != null }
  # list of pair of [(conn1, secret1), (conn1, secret2), ... (connN, secretM)]

  # NOTE: advantage of creating these, even if we expect customer to fill them manually, is that
  # will be deleted if `terraform destroy`
  enabled_oauth_secrets_to_create = distinct(flatten([
    for k, v in local.enabled_oauth_long_access_connectors : [
      for secret_var in v.secured_variables : {
        connector_name = k
        secret_name    = secret_var.name
      }
    ]
  ]))

  enabled_bulk_connectors = {
    for k, v in local.bulk_connectors : k => v if contains(var.enabled_connectors, k)
  }

  enabled_lockable_oauth_secrets_to_create = distinct(flatten([
    for k, v in local.enabled_oauth_long_access_connectors : [
      for secret_var in v.secured_variables : {
        connector_name = k
        secret_name    = secret_var.name
      } if try(secret_var.lockable, false) == true
    ]
  ]))
}