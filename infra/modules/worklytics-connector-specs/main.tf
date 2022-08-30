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
    }
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
    }
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
    }
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
    }
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
      required_oauth2_permission_scopes : [], # Delegated permissions (from `az ad sp list --query "[?appDisplayName=='Microsoft Graph'].oauth2Permissions" --all`)
      required_app_roles : [                  # Application permissions (form az ad sp list --query "[?appDisplayName=='Microsoft Graph'].appRoles" --all
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
        "/api/1.0/users",
        "/api/1.0/workspaces",
        "/api/1.0/workspaces/{ANY_WORKSPACE_ID}/projects",
        "/api/1.0/projects/{ANY_PROJECT_ID}/tasks",
        "/api/1.0/tasks/{ANY_TASK_ID}/stories",
      ]
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
      example_api_calls : []
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
  5. Copy the User OAuth Token
  6. If you are implementing the Proxy, then add the access token as `PSOXY_ACCESS_TOKEN_psoxy-slack-discovery-api` secret value in the Secret Manager for the Proxy
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
      example_api_calls : ["/v2/users"]
      example_api_calls_user_to_impersonate : null
      external_token_todo : <<EOT
## Zoom Setup

Zoom connector through Psoxy requires a custom managed app on the Zoom Marketplace (in development
mode, no need to publish).

1. Go to https://marketplace.zoom.us/develop/create and create an app of type JWT

2. Fill information and on App Credentials generate a token with a long expiration time, for example 00:00 01/01/2030

3. Copy the JWT Token, it will be used later when creating the Zoom cloud function.

4. Activate the app
EOT
    }
  }
}

output "enabled_google_workspace_connectors" {
  value = { for k, v in local.google_workspace_sources : k => v if contains(var.enabled_connectors, k) }
}

output "enabled_msft_365_connectors" {
  value = { for k, v in local.msft_365_connectors : k => v if contains(var.enabled_connectors, k) }
}

output "enabled_oauth_long_access_connectors" {
  value = { for k, v in local.oauth_long_access_connectors : k => v if contains(var.enabled_connectors, k) }
}


