locals {
  # Microsoft 365 sources; add/remove as you wish
  # See https://docs.microsoft.com/en-us/graph/permissions-reference for all the permissions available in AAD Graph API

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
    external_todo : null
    enable_side_output : false
    example_api_calls : [
      "/v1.0/users",
      "/v1.0/users/${var.example_msft_user_guid}",
      "/v1.0/groups",
      "/v1.0/groups/{group-id}/members"
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
      external_todo : null
      enable_side_output : false
      example_api_calls : [
        "/v1.0/users",
        "/v1.0/users/${var.example_msft_user_guid}/events",
        "/v1.0/users/${var.example_msft_user_guid}/calendarView?startDateTime=${timeadd(time_static.deployment.id, "-4320h")}&endDateTime=${time_static.deployment.id}",
        "/v1.0/users/${var.example_msft_user_guid}/mailboxSettings",
        "/v1.0/groups",
        "/v1.0/groups/{group-id}/members"
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
      external_todo : null
      enable_side_output : false
      example_api_calls : [
        "/v1.0/users",
        "/v1.0/users/${var.example_msft_user_guid}/mailboxSettings",
        "/v1.0/users/${var.example_msft_user_guid}/mailFolders/SentItems/messages",
        "/v1.0/groups",
        "/v1.0/groups/{group-id}/members"
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
        "/v1.0/teams/${var.msft_teams_example_team_guid}/allChannels",
        "/v1.0/users/${var.example_msft_user_guid}/chats",
        "/v1.0/teams/${var.msft_teams_example_team_guid}/channels/${var.msft_teams_example_channel_guid}/messages",
        "/v1.0/teams/${var.msft_teams_example_team_guid}/channels/${var.msft_teams_example_channel_guid}/messages/delta",
        "/v1.0/chats/${var.msft_teams_example_chat_guid}/messages",
        "/v1.0/communications/calls/${var.msft_teams_example_call_guid}",
        "/v1.0/communications/callRecords",
        "/v1.0/communications/callRecords/${var.msft_teams_example_call_record_guid}",
        "/v1.0/communications/callRecords/getDirectRoutingCalls(fromDateTime=${urlencode(timeadd(time_static.deployment.id, "-2160h"))},toDateTime=${urlencode(time_static.deployment.id)})",
        "/v1.0/communications/callRecords/getPstnCalls(fromDateTime=${urlencode(timeadd(time_static.deployment.id, "-2160h"))},toDateTime=${urlencode(time_static.deployment.id)})",
        "/v1.0/users/${var.example_msft_user_guid}/onlineMeetings?\\$filter=JoinWebUrl eq '${var.msft_teams_example_online_meeting_join_url}'"
      ]
      external_todo : <<EOT
To enable the connector, you need to allow permissions on the application created for reading OnlineMeetings. You will need Powershell for this.

Please follow the steps below:
1. Ensure the user you are going to use for running the commands has the "Teams Administrator" role. You can add the role in the
[Microsoft 365 Admin Center](https://learn.microsoft.com/en-us/microsoft-365/admin/add-users/assign-admin-roles?view=o365-worldwide#assign-a-user-to-an-admin-role-from-active-users)

**NOTE**: About the role, can be assigned through Entra Id portal in Azure portal OR in Entra Admin center https://admin.microsoft.com/AdminPortal/Home. It is possible that even login with an admin account in Entra Admin Center the Teams role is not available to assign to any user; if so, please do it through Azure Portal (Entra Id -> Users -> Assign roles)

2. Install [PowerShell Teams](https://learn.microsoft.com/en-us/microsoftteams/teams-powershell-install)  You can use `pwsh` in the terminal
    enter to PowerShell.
3. Then, run the following command. It will open a browser window for login to Microsoft Teams. After login, close the browser and return to the terminal.
   Please choose the user who has the "Teams Administrator" role.
```shell
Connect-MicrosoftTeams
```

4. Follow steps on [Configure application access to online meetings or virtual events](https://learn.microsoft.com/en-us/graph/cloud-communication-online-meeting-application-access-policy):
  - Add a policy for the application created for the connector, providing its `application id` (client ID)
```shell
New-CsApplicationAccessPolicy -Identity Teams-Policy-For-Worklytics -AppIds "%%entraid.client_id%%" -Description "Policy for MSFT Teams used for Worklytics Psoxy connector"
```
  - Grant the policy to the whole tenant (NOT to any specific application or user)
```shell
Grant-CsApplicationAccessPolicy -PolicyName Teams-Policy-For-Worklytics -Global
```

**Issues**:
- If you receive "access denied" is because no admin role for Teams has been detected. Please close and reopen the Powershell terminal after assigning the role.
- Commands have been tested over a Powershell (7.4.0) terminal in Windows, installed from Microsoft Store and with Teams Module (5.8.0). It might not work on a different environment
EOT
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
      external_todo : null
      enable_side_output : false
      example_api_calls : [
        "/v1.0/users",
        "/beta/copilot/users/${var.example_msft_user_guid}/interactionHistory/getAllEnterpriseInteractions"
      ]
    }
  }
}
