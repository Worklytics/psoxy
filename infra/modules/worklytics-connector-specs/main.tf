locals {
  google_workspace_sources = {
    # GDirectory connections are a PRE-REQ for gmail, gdrive, and gcal connections. remove only
    # if you plan to directly connect Directory to worklytics (without proxy). such a scenario is
    # used for customers who care primarily about pseudonymizing PII of external subjects with whom
    # they collaborate in GMail/GCal/GDrive. the Directory does not contain PII of subjects external
    # to the Google Workspace, so may be directly connected in such scenarios.
    "gdirectory" : {
      source_kind : "gdirectory",
      display_name : "Google Directory"
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
      example_api_calls : [
        "/admin/directory/v1/users/me",
        "/admin/directory/v1/users?customer=my_customer",
        "/admin/directory/v1/groups?customer=my_customer",
        "/admin/directory/v1/customer/my_customer/domains",
        "/admin/directory/v1/customer/my_customer/roles",
        "/admin/directory/v1/customer/my_customer/rolesassignments"
      ]
      example_api_calls_user_to_impersonate : var.google_workspace_example_user
    },
    "gcal" : {
      source_kind : "gcal",
      display_name : "Google Calendar"
      apis_consumed : [
        "calendar-json.googleapis.com"
      ]
      oauth_scopes_needed : [
        "https://www.googleapis.com/auth/calendar.readonly"
      ],
      example_api_calls : [
        "/calendar/v3/calendars/primary",
        "/calendar/v3/users/me/settings",
        "/calendar/v3/calendars/primary/events"
      ]
      example_api_calls_user_to_impersonate : var.google_workspace_example_user
    },
    "gmail" : {
      source_kind : "gmail",
      display_name : "GMail"
      apis_consumed : [
        "gmail.googleapis.com"
      ]
      oauth_scopes_needed : [
        "https://www.googleapis.com/auth/gmail.metadata"
      ],
      example_api_calls : [
        "/gmail/v1/users/me/messages"
      ]
      example_api_calls_user_to_impersonate : var.google_workspace_example_user
    },
    "google-chat" : {
      source_kind : "google-chat",
      display_name : "Google Chat"
      apis_consumed : [
        "admin.googleapis.com"
      ]
      oauth_scopes_needed : [
        "https://www.googleapis.com/auth/admin.reports.audit.readonly"
      ]
      example_api_calls : [
        "/admin/reports/v1/activity/users/all/applications/chat"
      ]
      example_api_calls_user_to_impersonate : var.google_workspace_example_user
    },
    "google-meet" : {
      source_kind : "google-meet",
      display_name : "Google Meet"
      apis_consumed : [
        "admin.googleapis.com"
      ]
      oauth_scopes_needed : [
        "https://www.googleapis.com/auth/admin.reports.audit.readonly"
      ]
      example_api_calls : [
        "/admin/reports/v1/activity/users/all/applications/meet"
      ]
      example_api_calls_user_to_impersonate : var.google_workspace_example_user
    },
    "gdrive" : {
      source_kind : "gdrive",
      display_name : "Google Drive"
      apis_consumed : [
        "drive.googleapis.com"
      ]
      oauth_scopes_needed : [
        "https://www.googleapis.com/auth/drive.metadata.readonly"
      ],
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
      source_kind : "azure-ad",
      display_name : "Azure Directory"
      required_oauth2_permission_scopes : [],
      # Delegated permissions (from `az ad sp list --query "[?appDisplayName=='Microsoft Graph'].oauth2Permissions" --all`)
      required_app_roles : [
        # Application permissions (form az ad sp list --query "[?appDisplayName=='Microsoft Graph'].appRoles" --all
        "User.Read.All",
        "Group.Read.All"
      ],
      example_calls : [
        "/v1.0/users",
        "/v1.0/groups"
      ]
    },
    "outlook-cal" : {
      enabled : true,
      source_kind : "outlook-cal",
      display_name : "Outlook Calendar"
      required_oauth2_permission_scopes : [],
      required_app_roles : [
        "OnlineMeetings.Read.All",
        "Calendars.Read",
        "MailboxSettings.Read",
        "Group.Read.All",
        "User.Read.All"
      ],
      example_calls : [
        "/v1.0/users",
        "/v1.0/users/${var.example_msft_user_guid}/events",
        "/v1.0/users/${var.example_msft_user_guid}/mailboxSettings"
      ]
    },
    "outlook-mail" : {
      enabled : true,
      source_kind : "outlook-mail"
      display_name : "Outlook Mail"
      required_oauth2_permission_scopes : [],
      required_app_roles : [
        "Mail.ReadBasic.All",
        "MailboxSettings.Read",
        "Group.Read.All",
        "User.Read.All"
      ],
      example_calls : [
        "/beta/users",
        "/beta/users/${var.example_msft_user_guid}/mailboxSettings",
        "/beta/users/${var.example_msft_user_guid}/mailFolders/SentItems/messages"
      ]
    }
  }
  oauth_long_access_connectors = {
    asana = {
      source_kind : "asana",
      display_name : "Asana"
      example_api_calls : [
        "/api/1.0/workspaces",
        "/api/1.0/users?workspaceId={ANY_WORKSPACE_ID}&limit=10",
        "/api/1.0/workspaces/{ANY_WORKSPACE_ID}/projects?limit=20",
        "/api/1.0/projects/{ANY_PROJECT_ID}/tasks?limit=20",
        "/api/1.0/tasks/{ANY_TASK_ID}/stories",
      ],
      secured_variables : [
        "ACCESS_TOKEN",
      ],
      reserved_concurrent_executions : null
      example_api_calls_user_to_impersonate : null
      external_token_todo : <<EOT
  1. Create a [Service Account User + token](https://asana.com/guide/help/premium/service-accounts)
     or a sufficiently [Personal Access Token]() for a sufficiently privileged user (who can see all
     the workspaces/teams/projects/tasks you wish to import to Worklytics via this connection).
EOT
    }
    slack-discovery-api = {
      source_kind : "slack"
      display_name : "Slack Discovery API"
      example_api_calls : [
        "/api/discovery.enterprise.info",
        "/api/discovery.conversations.list",
        "/api/discovery.conversations.history?channel={CHANNEL_ID}&limit=10",
        "/api/discovery.users.list",
      ],
      secured_variables : [
        "ACCESS_TOKEN",
      ]
      reserved_concurrent_executions : null
      example_api_calls_user_to_impersonate : null
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
      display_name : "Zoom"
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
      secured_variables : [
        "CLIENT_SECRET",
        "CLIENT_ID",
        "ACCOUNT_ID",
        "WRITABLE_ACCESS_TOKEN"
      ],
      reserved_concurrent_executions : null # 1
      example_api_calls_user_to_impersonate : null
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
      source_kind : "dropbox-business",
      display_name : "Dropbox Business"
      example_api_calls : [
        "/2/team/members/list_v2",
        "/2/team/groups/list",
        "/2/team_log/get_events",
      ],
      secured_variables : [
        "REFRESH_TOKEN",
        "CLIENT_ID",
        "CLIENT_SECRET"
      ],
      token_endpoint : "https://api.dropboxapi.com/oauth2/token",
      reserved_concurrent_executions : null
      example_api_calls_user_to_impersonate : null
      external_token_todo : <<EOT
Dropbox connector through Psoxy requires a Dropbox Application created in Dropbox Console. The application
does not require to be public, and it needs to have the following scopes to support
all the operations for the connector:

- files.metadata.read: for file listing and revision
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
  - `PSOXY_dropbox_business_REFRESH_TOKEN` secret variable with value of `refresh_token` received in previous response
  - `PSOXY_dropbox_business_CLIENT_ID` with `App key` value.
  - `PSOXY_dropbox_business_CLIENT_SECRET` with `App secret` value.

EOT
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
      for secret_name in v.secured_variables : {
        connector_name = k
        secret_name    = secret_name
      }
    ]
  ]))
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
