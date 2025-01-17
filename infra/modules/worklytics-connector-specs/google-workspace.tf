locals {

  google_workspace_example_user = coalesce(var.google_workspace_example_user, "REPLACE_WITH_EXAMPLE_USER@YOUR_COMPANY.COM")
  google_workspace_example_admin = coalesce(var.google_workspace_example_admin, var.google_workspace_example_user, "REPLACE_WITH_EXAMPLE_ADMIN@YOUR_COMPANY.COM")
  google_workspace_sources = {
    # GDirectory connections are a PRE-REQ for gmail, gdrive, and gcal connections. remove only
    # if you plan to directly connect Directory to worklytics (without proxy). such a scenario is
    # used for customers who care primarily about pseudonymizing PII of external subjects with whom
    # they collaborate in GMail/GCal/GDrive. the Directory does not contain PII of subjects external
    # to the Google Workspace, so may be directly connected in such scenarios.
    "gdirectory" : {
      source_kind : "gdirectory",
      availability : "ga",
      enable_by_default : true
      worklytics_connector_id : "gdirectory-psoxy",
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
      ]
      source_auth_strategy : "gcp_service_account_key"
      target_host : "admin.googleapis.com"
      environment_variables : {}
      example_api_calls : [
        "/admin/directory/v1/users?customer=my_customer&maxResults=10",
        "/admin/directory/v1/users/{USER_ID}",
        "/admin/directory/v1/groups?customer=my_customer&maxResults=10",
        "/admin/directory/v1/groups/{GROUP_ID}",
        "/admin/directory/v1/groups/{GROUP_ID}/members?maxResults=10",
        "/admin/directory/v1/customer/my_customer/domains",
        "/admin/directory/v1/customer/my_customer/orgunits?maxResults=10",
      ]
      example_api_calls_user_to_impersonate : local.google_workspace_example_admin
    },
    "gcal" : {
      source_kind : "gcal",
      availability : "ga",
      enable_by_default : true
      worklytics_connector_id : "gcal-psoxy",
      display_name : "Google Calendar"
      apis_consumed : [
        "calendar-json.googleapis.com"
      ]
      source_auth_strategy : "gcp_service_account_key"
      target_host : "www.googleapis.com"
      oauth_scopes_needed : [
        "https://www.googleapis.com/auth/calendar.readonly"
      ]
      environment_variables : {}
      example_api_calls : [
        "/calendar/v3/calendars/primary",
        "/calendar/v3/users/me/settings",
        "/calendar/v3/users/me/calendarList",
        "/calendar/v3/calendars/primary/events?maxResults=10",
        "/calendar/v3/calendars/primary/events/{EVENT_ID}"
      ]
      example_api_calls_user_to_impersonate : local.google_workspace_example_user
    },
    "gmail" : {
      source_kind : "gmail",
      availability : "ga",
      enable_by_default : false,
      worklytics_connector_id : "gmail-meta-psoxy",
      display_name : "GMail"
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
        "/gmail/v1/users/me/messages?maxResults=5&labelIds=SENT",
        "/gmail/v1/users/me/messages/{MESSAGE_ID}?format=metadata"
      ]
      example_api_calls_user_to_impersonate : local.google_workspace_example_user
    },
    "google-chat" : {
      source_kind : "google-chat",
      availability : "ga",
      enable_by_default : false
      worklytics_connector_id : "google-chat-psoxy",
      display_name : "Google Chat"
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
      availability : "ga",
      enable_by_default : false
      worklytics_connector_id : "google-meet-psoxy"
      display_name : "Google Meet"
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
      availability : "ga",
      enable_by_default : false
      worklytics_connector_id : "gdrive-psoxy",
      display_name : "Google Drive"
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
        "/drive/v3/files",
        "/drive/v3/files/{FILE_ID}",
        "/drive/v3/files/{FILE_ID}/permissions",
        "/drive/v3/files/{FILE_ID}/revisions"
      ],
      example_api_calls_user_to_impersonate : local.google_workspace_example_user
    }
  }
}
