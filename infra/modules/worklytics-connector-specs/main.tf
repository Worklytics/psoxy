
# TODO: arguably it does make sense to have these in yaml, and read them from there; bc YAML gives
# more interoperability than in a .tf file


locals {

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
        "/admin/directory/v1/customer/my_customer/roles?maxResults=10",
        "/admin/directory/v1/customer/my_customer/roleassignments?maxResults=10"
      ]
      example_api_calls_user_to_impersonate : var.google_workspace_example_admin
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
      example_api_calls_user_to_impersonate : var.google_workspace_example_user
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
      example_api_calls_user_to_impersonate : var.google_workspace_example_user
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
      example_api_calls_user_to_impersonate : var.google_workspace_example_user
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
      example_api_calls_user_to_impersonate : var.google_workspace_example_user
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
      example_api_calls_user_to_impersonate : var.google_workspace_example_user
    }
  }

  # Microsoft 365 sources; add/remove as you wish
  # See https://docs.microsoft.com/en-us/graph/permissions-reference for all the permissions available in AAD Graph API
  msft_365_connectors = {
    "azure-ad" : {
      enabled : true,
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
      enabled : true,
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
      enabled : true,
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
        GRANT_TYPE : "workload_identity_federation" # by default, assumed to be of type 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer'
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
      ],
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
      ],
      secured_variables : [
        { name : "ACCESS_TOKEN", writable : false },
      ],
      external_token_todo : <<EOT
  1. Create a [Service Account User + token](https://asana.com/guide/help/premium/service-accounts)
    or a sufficiently [Personal Access Token](https://developers.asana.com/docs/personal-access-token)
    for a sufficiently privileged user (who can see all the workspaces/teams/projects/tasks you wish to
    import to Worklytics via this connection).
  2. Update the content of PSOXY_ASANA_ACCESS_TOKEN variable with the previous token value obtained
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
## Slack Discovery Setup
For enabling Slack Discovery with the Psoxy you must first setup an app on your Slack Enterprise
instance.
  1. Go to https://api.slack.com/apps and create an app, select name a development workspace
  2. Take note of your App ID and contact your Slack rep and ask them to enable `discovery:read` scope for the app.
If they also enable `discovery:write` then delete it for safety, the app just needs read access.
3. Generate the following URL replacing the placeholders for *YOUR_CLIENT_ID* and *YOUR_APP_SECRET* and save it for later
`https://api.slack.com/api/oauth.v2.access?client_id=YOUR_CLIENT_ID&client_secret=YOUR_APP_SECRET`
4. Go to OAuth & Permissions > Redirect URLs and add the previous URL there
The next step depends on your installation approach you might need to change slightly
### Org wide install
Use this step if you want to install in the whole org, across multiple workspaces.
  1. Add a bot scope (not really used, but Slack doesn't allow org-wide without a bot scope requested).
     Just add `users:read`, something that is read-only and we already have access through discovery.
  2. Go to *Org Level Apps* and Opt-in to the program
  3. Go to Settings > Install App
  4. Install into *organization*
  5. Copy the User OAuth Token and store it in secret manager.
  Otherwise, share the token with the AWS/GCP administrator completing the implementation.
### Workspace install
Use this steps if you intend to install in just one workspace within your org.
  1. Go to Settings > Install App
  2. Install into *workspace*
  3. Copy the User OAuth Token and store it in the secret manager (or share with the administrator completing the implementation)
EOT
    }
    zoom = {
      source_kind : "zoom"
      worklytics_connector_id : "zoom-psoxy"
      display_name : "Zoom"
      worklytics_connector_name : "Zoom via Psoxy"
      identifier_scope_id : "zoom"
      source_auth_strategy: "oauth2_refresh_token"
      target_host: "api.zoom.us"
      environment_variables : {
        GRANT_TYPE: "account_credentials"
        REFRESH_ENDPOINT: "https://zoom.us/oauth/token"
      }
      secured_variables : [
        { name : "CLIENT_SECRET", writable : false },
        { name : "CLIENT_ID", writable : false },
        { name : "ACCOUNT_ID", writable : false },
        { name : "ACCESS_TOKEN", writable : true },
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
      target_host: "api.dropboxapi.com"
      source_auth_strategy: "oauth2_refresh_token"
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
    }
    "hris" = {
      source_kind               = "hris"
      worklytics_connector_id   = "bulk-import-psoxy"
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
    }
  }
}

# computed values filtered by enabled connectors
locals {
  enabled_google_workspace_connectors = {
    for k, v in local.google_workspace_sources : k => v if contains(var.enabled_connectors, k)
  }
  enabled_msft_365_connectors = {
    for k, v in local.msft_365_connectors : k => v if contains(var.enabled_connectors, k)
  }
  enabled_oauth_long_access_connectors = { for k, v in local.oauth_long_access_connectors : k => v if contains(var.enabled_connectors, k) }

  enabled_oauth_long_access_connectors_todos = { for k, v in local.enabled_oauth_long_access_connectors : k => v if v.external_token_todo != null }
  # list of pair of [(conn1, secret1), (conn1, secret2), ... (connN, secretM)]

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
}

output "enabled_google_workspace_connectors" {
  value = local.enabled_google_workspace_connectors
}

output "enabled_msft_365_connectors" {
  value = local.enabled_msft_365_connectors
}

output "enabled_oauth_long_access_connectors" {
  value = local.enabled_oauth_long_access_connectors
}

output "enabled_oauth_long_access_connectors_todos" {
  value = local.enabled_oauth_long_access_connectors_todos
}

output "enabled_oauth_secrets_to_create" {
  value = local.enabled_oauth_secrets_to_create
}

output "enabled_bulk_connectors" {
  value = local.enabled_bulk_connectors
}
