locals {
  # Microsoft 365 sources; add/remove as you wish
  # See https://learn.microsoft.com/en-us/graph/permissions-reference for all the permissions available in Microsoft Graph API

  # these are the same for all the Microsoft 365 connectors
  msft_365_environment_variables = {
    GRANT_TYPE : "workload_identity_federation"
    # by default, assumed to be of type 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer'
    TOKEN_SCOPE : "https://graph.microsoft.com/.default"
    REFRESH_ENDPOINT = "https://login.microsoftonline.com/${var.msft_tenant_id}/oauth2/v2.0/token"
  }

  entra_id_prototype = {
    worklytics_connector_id : "azure-ad-psoxy",
    availability : "ga",
    enable_by_default : false,
    # really, ONLY do Outlook Cal in the min-case; get users and workday settings from there
    source_kind : "azure-ad",
    display_name : "Microsoft Entra ID (former Azure AD)"
    source_auth_strategy : "oauth2_refresh_token"
    target_host : "graph.microsoft.com"
    required_oauth2_permission_scopes : []
    # Delegated permissions (from `az ad sp list --query "[?appDisplayName=='Microsoft Graph'].oauth2Permissions" --all`)
    required_app_roles : [
      # Application permissions (form az ad sp list --query "[?appDisplayName=='Microsoft Graph'].appRoles" --all
      "User.Read.All",
      "Group.Read.All",
      "MailboxSettings.Read"
    ]
    environment_variables : local.msft_365_environment_variables
    external_token_todo : null
    enable_side_output : false
    example_api_calls : [
      "/v1.0/users",
      "/v1.0/users/${local.example_msft_user_guid}",
      "/v1.0/groups",
      "/v1.0/groups/${local.example_msft_group_guid}/members"
    ]
  }

  msft_365_connectors = {
    # azure-ad is legacy branding of `entra`; so re-use prototype, but override some fields
    "azure-ad" : merge(local.entra_id_prototype, {
      availability : "deprecated",
      enable_by_default : false,
      source_kind : "azure-ad",
      display_name : "(Deprecated, use MSFT Entra Id instead) Azure Directory"
    }),
    "msft-entra-id" : local.entra_id_prototype,
    "outlook-cal" : {
      source_kind : "outlook-cal",
      availability : "ga",
      enable_by_default : true,
      worklytics_connector_id : "outlook-cal-psoxy",
      display_name : "Outlook Calendar"
      source_auth_strategy : "oauth2_refresh_token"
      target_host : "graph.microsoft.com"
      required_oauth2_permission_scopes : []
      required_app_roles : [
        "Calendars.Read",
        "MailboxSettings.Read",
        "Group.Read.All",
        "User.Read.All"
      ],
      environment_variables : local.msft_365_environment_variables
      external_token_todo : null
      enable_side_output : false
      example_api_calls : [
        "/v1.0/users",
        "/v1.0/users?\\$select=id,mail,otherMails",
        "/v1.0/users/${local.example_msft_user_guid}/events",
        "/v1.0/users/${local.example_msft_user_guid}/calendar/calendarView?startDateTime=${timeadd(var.example_api_calls_sample_date, "-4320h")}&endDateTime=${var.example_api_calls_sample_date}",
        "/v1.0/users/${local.example_msft_user_guid}/mailboxSettings",
        "/v1.0/groups",
        "/v1.0/groups/${local.example_msft_group_guid}/members"
      ]
    },
    "outlook-mail" : {
      source_kind : "outlook-mail"
      availability : "ga",
      enable_by_default : false,
      worklytics_connector_id : "outlook-mail-psoxy",
      display_name : "Outlook Mail"
      source_auth_strategy : "oauth2_refresh_token"
      target_host : "graph.microsoft.com"
      required_oauth2_permission_scopes : []
      required_app_roles : [
        "Mail.ReadBasic.All",
        "MailboxSettings.Read",
        "Group.Read.All",
        "User.Read.All"
      ]
      environment_variables : local.msft_365_environment_variables
      external_token_todo : null
      enable_side_output : false
      example_api_calls : [
        "/v1.0/users",
        "/v1.0/users?\\$select=id,mail,otherMails",
        "/v1.0/users/${local.example_msft_user_guid}/mailboxSettings",
        "/v1.0/users/${local.example_msft_user_guid}/mailFolders/SentItems/messages",
        "/v1.0/groups",
        "/v1.0/groups/${local.example_msft_group_guid}/members"
      ]
    },
    "msft-onedrive" : {
      source_kind : "msft-onedrive"
      availability : "beta",
      enable_by_default : false,
      worklytics_connector_id : "msft-onedrive-psoxy",
      display_name : "Microsoft OneDrive"
      source_auth_strategy : "oauth2_refresh_token"
      target_host : "graph.microsoft.com"
      required_oauth2_permission_scopes : []
      required_app_roles : [
        # least-privilege permission for enumerating users'/groups' drives and reading the
        # driveItem delta and activities feeds this connector calls.
        "Files.Read.All",
        # to enumerate the users/groups whose OneDrives are polled; this connector also requires
        # a separate Microsoft Entra ID connection to be configured.
        "User.Read.All",
        "Group.Read.All",
      ]
      environment_variables : local.msft_365_environment_variables
      external_token_todo : null
      enable_side_output : false
      example_api_calls : [
        "/v1.0/users",
        "/v1.0/groups",
        "/v1.0/users/${local.example_msft_user_guid}/drives",
        "/v1.0/groups/${local.example_msft_group_guid}/drives",
        "/v1.0/drives/${local.msft_onedrive_example_drive_id}/root/delta",
        "/v1.0/drives/${local.msft_onedrive_example_drive_id}/items/${local.msft_onedrive_example_item_id}/activities",
        "/v1.0/drives/${local.msft_onedrive_example_drive_id}/activities",
      ]
    },
    "msft-teams" : {
      source_kind : "msft-teams"
      availability : "ga",
      enable_by_default : false,
      worklytics_connector_id : "msft-teams-psoxy",
      display_name : "Microsoft Teams"
      source_auth_strategy : "oauth2_refresh_token"
      target_host : "graph.microsoft.com"
      required_oauth2_permission_scopes : [],
      required_app_roles : [
        "User.Read.All",
        "Team.ReadBasic.All",
        "Channel.ReadBasic.All",
        "Chat.Read.All",
        "ChannelMessage.Read.All",
        "CallRecords.Read.All",
        "OnlineMeetings.Read.All",
        "OnlineMeetingArtifact.Read.All"
      ],
      environment_variables : local.msft_365_environment_variables
      enable_side_output : false
      example_api_calls : [
        "/v1.0/teams",
        "/v1.0/teams/${local.msft_teams_example_team_guid}/allChannels",
        "/v1.0/users/${local.example_msft_user_guid}/chats",
        "/v1.0/teams/${local.msft_teams_example_team_guid}/channels/${local.msft_teams_example_channel_guid}/messages",
        "/v1.0/teams/${local.msft_teams_example_team_guid}/channels/${local.msft_teams_example_channel_guid}/messages/delta",
        "/v1.0/chats/${local.msft_teams_example_chat_guid}/messages",
        "/v1.0/communications/calls/${local.msft_teams_example_call_guid}",
        "/v1.0/communications/callRecords",
        "/v1.0/communications/callRecords/${local.msft_teams_example_call_record_guid}",
        "/v1.0/communications/callRecords/getDirectRoutingCalls(fromDateTime=${urlencode(timeadd(var.example_api_calls_sample_date, "-2160h"))},toDateTime=${urlencode(var.example_api_calls_sample_date)})",
        "/v1.0/communications/callRecords/getPstnCalls(fromDateTime=${urlencode(timeadd(var.example_api_calls_sample_date, "-2160h"))},toDateTime=${urlencode(var.example_api_calls_sample_date)})",
        "/v1.0/users/${local.example_msft_user_guid}/onlineMeetings?\\$filter=JoinWebUrl eq '${local.msft_teams_example_online_meeting_join_url}'"
      ]
      external_token_todo : templatefile("${path.module}/docs/msft-teams/instructions.tftpl", {})
    },
    "msft-copilot" : {
      source_kind : "msft-copilot"
      availability : "alpha",
      enable_by_default : false,
      worklytics_connector_id : "msft-copilot-psoxy",
      display_name : "Microsoft 365 Copilot"
      source_auth_strategy : "oauth2_refresh_token"
      target_host : "graph.microsoft.com"
      required_oauth2_permission_scopes : []
      required_app_roles : [
        "User.Read.All",
        "AiEnterpriseInteraction.Read.All"
      ]
      environment_variables : local.msft_365_environment_variables
      external_token_todo : null
      enable_side_output : false
      example_api_calls : [
        "/v1.0/users",
        "/beta/copilot/users/${local.example_msft_user_guid}/interactionHistory/getAllEnterpriseInteractions"
      ]
    }
  }
}
