
# TODO: arguably it does make sense to have these in yaml, and read them from there; bc YAML gives
# more interoperability than in a .tf file


# initial deployment time; in effect, timestamp of first `terraform apply`; will persist into the
# terraform state.
# we derive various API example calls from this; if we used `timestamp()` method, the example TODOs
# and scripts would show changes on every apply
# see https://registry.terraform.io/providers/hashicorp/time/latest/docs/resources/static
# q: possibly better to declare this at root, and pass in as a variable? would reduce noise
#    (one resource, instead of 3 because 'worklytics-connector-specs' is reference 3 times)
resource "time_static" "deployment" {

}

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
      availability : "ga",
      enable_by_default : true
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
      identifier_scope_id : "gapps"
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
      enable_by_default : true
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
      availability : "ga",
      enable_by_default : false
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
      availability : "ga",
      enable_by_default : false
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
        "/drive/v3/files",
        "/drive/v3/files/{FILE_ID}",
        "/drive/v3/files/{FILE_ID}/permissions",
        "/drive/v3/files/{FILE_ID}/revisions"
      ],
      example_api_calls_user_to_impersonate : local.google_workspace_example_user
    }
  }

  # backwards-compatible for v0.4.x; remove in v0.5.x
  google_workspace_sources_backwards = { for k, v in local.google_workspace_sources :
  k => merge(v, { example_calls : v.example_api_calls }) }


  jira_example_cloud_id            = coalesce(var.jira_cloud_id, "YOUR_JIRA_CLOUD_ID")
  jira_example_issue_id            = coalesce(var.jira_example_issue_id, var.example_jira_issue_id, "YOUR_JIRA_EXAMPLE_ISSUE_ID")
  github_installation_id           = coalesce(var.github_installation_id, "YOUR_GITHUB_INSTALLATION_ID")
  github_enterprise_server_host    = coalesce(var.github_api_host, var.github_enterprise_server_host, "YOUR_GITHUB_ENTERPRISE_SERVER_HOST")
  github_enterprise_server_version = coalesce(var.github_enterprise_server_version, "v3")
  github_organization              = coalesce(var.github_organization, "YOUR_GITHUB_ORGANIZATION_NAME")
  github_first_organization        = split(",", coalesce(var.github_organization, "YOUR_GITHUB_ORGANIZATION_NAME"))[0]
  github_example_repository        = coalesce(var.github_example_repository, "YOUR_GITHUB_EXAMPLE_REPOSITORY_NAME")
  salesforce_example_account_id    = coalesce(var.salesforce_example_account_id, "{ANY ACCOUNT ID}")

  # Microsoft 365 sources; add/remove as you wish
  # See https://docs.microsoft.com/en-us/graph/permissions-reference for all the permissions available in AAD Graph API
  msft_365_connectors = {
    "azure-ad" : {
      worklytics_connector_id : "azure-ad-psoxy",
      availability : "deprecated",
      enable_by_default : false,
      source_kind : "azure-ad",
      display_name : "(Deprecated, use MSFT Entra Id instead) Azure Directory"
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
      }
      external_todo : null
      example_api_calls : [
        "/v1.0/users",
        "/v1.0/users/${var.example_msft_user_guid}",
        "/v1.0/groups",
        "/v1.0/groups/{group-id}/members"
      ]
    },
    "msft-entra-id" : {
      worklytics_connector_id : "azure-ad-psoxy",
      availability : "ga",
      enable_by_default : true,
      source_kind : "azure-ad",
      display_name : "Microsoft Entra ID (former Azure AD)"
      identifier_scope_id : "azure-ad"
      source_auth_strategy : "oauth2_refresh_token"
      target_host : "graph.microsoft.com"
      required_oauth2_permission_scopes : []
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
      }
      external_todo : null
      example_api_calls : [
        "/v1.0/users",
        "/v1.0/users/${var.example_msft_user_guid}",
        "/v1.0/groups",
        "/v1.0/groups/{group-id}/members"
      ]
    },
    "outlook-cal" : {
      source_kind : "outlook-cal",
      availability : "ga",
      enable_by_default : true,
      worklytics_connector_id : "outlook-cal-psoxy",
      display_name : "Outlook Calendar"
      identifier_scope_id : "azure-ad"
      source_auth_strategy : "oauth2_refresh_token"
      target_host : "graph.microsoft.com"
      required_oauth2_permission_scopes : []
      required_app_roles : [
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
      external_todo : null
      example_api_calls : [
        "/v1.0/users",
        "/v1.0/users/${var.example_msft_user_guid}/events",
        "/v1.0/users/${var.example_msft_user_guid}/calendarView?startDateTime=2022-10-01T00:00:00Z&endDateTime=${time_static.deployment.id}",
        "/v1.0/users/${var.example_msft_user_guid}/mailboxSettings",
        "/v1.0/groups",
        "/v1.0/groups/{group-id}/members"
      ]
    },
    "outlook-mail" : {
      source_kind : "outlook-mail"
      availability : "ga",
      enable_by_default : true,
      worklytics_connector_id : "outlook-mail-psoxy",
      display_name : "Outlook Mail"
      identifier_scope_id : "azure-ad"
      source_auth_strategy : "oauth2_refresh_token"
      target_host : "graph.microsoft.com"
      required_oauth2_permission_scopes : []
      required_app_roles : [
        "Mail.ReadBasic.All",
        "MailboxSettings.Read",
        "Group.Read.All",
        "User.Read.All"
      ]
      environment_variables : {
        GRANT_TYPE : "workload_identity_federation"
        # by default, assumed to be of type 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer'
        TOKEN_SCOPE : "https://graph.microsoft.com/.default"
        REFRESH_ENDPOINT : "https://login.microsoftonline.com/${var.msft_tenant_id}/oauth2/v2.0/token"
      }
      external_todo : null
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
      availability : "beta",
      enable_by_default : false,
      worklytics_connector_id : "msft-teams-psoxy",
      display_name : "Microsoft Teams"
      identifier_scope_id : "azure-ad"
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
      environment_variables : {
        GRANT_TYPE : "workload_identity_federation"
        # by default, assumed to be of type 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer'
        TOKEN_SCOPE : "https://graph.microsoft.com/.default"
        REFRESH_ENDPOINT : "https://login.microsoftonline.com/${var.msft_tenant_id}/oauth2/v2.0/token"
      }
      example_api_calls : [
        "/v1.0/teams",
        "/v1.0/teams/${var.msft_teams_example_team_guid}/allChannels",
        "/v1.0/users/${var.example_msft_user_guid}/chats",
        "/v1.0/teams/${var.msft_teams_example_team_guid}/channels/${var.msft_teams_example_channel_guid}/messages",
        "/v1.0/teams/${var.msft_teams_example_team_guid}/channels/${var.msft_teams_example_channel_guid}/messages/delta",
        "/v1.0/chats/${var.msft_teams_example_chat_guid}/messages",
        "/v1.0/communications/calls/${var.msft_teams_example_call_guid}",
        "/v1.0/communications/callRecords/${var.msft_teams_example_call_record_guid}",
        "/v1.0/communications/callRecords/getDirectRoutingCalls(fromDateTime=${urlencode(timeadd(time_static.deployment.id, "-2160h"))},toDateTime=${urlencode(time_static.deployment.id)})",
        "/v1.0/communications/callRecords/getPstnCalls(fromDateTime=${urlencode(timeadd(time_static.deployment.id, "-2160h"))},toDateTime=${urlencode(time_static.deployment.id)})",
        "/v1.0/users/${var.example_msft_user_guid}/onlineMeetings"
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
    }
  }

  # backwards-compatible for v0.4.x; remove in v0.5.x
  msft_365_connectors_backwards = { for k, v in local.msft_365_connectors :
  k => merge(v, { example_calls : v.example_api_calls }) }


  oauth_long_access_connectors = {
    asana = {
      source_kind : "asana",
      availability : "ga",
      enable_by_default : false,
      worklytics_connector_id : "asana-psoxy"
      display_name : "Asana"
      identifier_scope_id : "asana"
      worklytics_connector_name : "Asana via Psoxy"
      target_host : "app.asana.com"
      source_auth_strategy : "oauth2_access_token"
      environment_variables : {}
      secured_variables : [
        {
          name : "ACCESS_TOKEN"
          writable : false
          sensitive : true
          value_managed_by_tf : false
        },
      ]
      reserved_concurrent_executions : null
      example_api_calls_user_to_impersonate : null
      example_api_calls : [
        "/api/1.0/workspaces",
        "/api/1.0/users?workspace={ANY_WORKSPACE_GID}&limit=10",
        "/api/1.0/workspaces/{ANY_WORKSPACE_GID}/teams?limit=10",
        "/api/1.0/teams/{ANY_TEAM_GID}/projects?limit=20",
        "/api/1.0/tasks?project={ANY_PROJECT_GID}&limit=10",
        "/api/1.0/tasks/{ANY_TASK_GID}",
        "/api/1.0/tasks/{ANY_TASK_GID}/stories",
      ]
      external_token_todo : <<EOT
  1. Create a [Service Account User + token](https://asana.com/guide/help/premium/service-accounts)
    or a [Personal Access Token](https://developers.asana.com/docs/personal-access-token) for a
    sufficiently privileged user (who can see all the workspaces/teams/projects/tasks you wish to
    import to Worklytics via this connection).
  2. Update the content of PSOXY_ASANA_ACCESS_TOKEN variable with the previous token value obtained
EOT
    }
    github = {
      source_kind : "github",
      availability : "ga",
      enable_by_default : true,
      worklytics_connector_id : "github-enterprise-psoxy"
      display_name : "Github Enterprise"
      identifier_scope_id : "github"
      worklytics_connector_name : "Github Enterprise via Psoxy"
      target_host : "api.github.com"
      source_auth_strategy : "oauth2_refresh_token"
      secured_variables : [
        {
          name : "ACCESS_TOKEN"
          writable : true
          sensitive : true
          value_managed_by_tf : false
        },
        {
          name : "PRIVATE_KEY"
          writable : false
          sensitive : true
          value_managed_by_tf : false
        },
        {
          name : "CLIENT_ID"
          writable : false
          sensitive : true
          value_managed_by_tf : false
        },
        {
          name : "OAUTH_REFRESH_TOKEN"
          writable : true
          lockable : true
          sensitive : true
          value_managed_by_tf : false
        }
      ],
      environment_variables : {
        GRANT_TYPE : "certificate_credentials"
        TOKEN_RESPONSE_TYPE : "GITHUB_ACCESS_TOKEN"
        REFRESH_ENDPOINT : "https://api.github.com/app/installations/${local.github_installation_id}/access_tokens"
        USE_SHARED_TOKEN : "TRUE"
      }
      settings_to_provide = {
        "GitHub Organization" = local.github_organization
      }
      reserved_concurrent_executions : null
      example_api_calls_user_to_impersonate : null
      example_api_calls : [
        "/orgs/${local.github_organization}/repos",
        "/orgs/${local.github_organization}/members",
        "/orgs/${local.github_organization}/teams",
        "/orgs/${local.github_organization}/audit-log",
        "/repos/${local.github_organization}/${local.github_example_repository}/events",
        "/repos/${local.github_organization}/${local.github_example_repository}/commits",
        "/repos/${local.github_organization}/${local.github_example_repository}/issues",
        "/repos/${local.github_organization}/${local.github_example_repository}/pulls",
      ]
      external_token_todo : <<EOT
  1. Populate `github_organization` variable in Terraform with the name of your GitHub organization.
  2. From your organization, register a [GitHub App](https://docs.github.com/en/apps/creating-github-apps/registering-a-github-app/registering-a-github-app#registering-a-github-app)
    with following permissions with **Read Only**:
    - Repository:
      - Contents: for reading commits and comments
      - Issues: for listing issues, comments, assignees, etc.
      - Metadata: for listing repositories and branches
      - Pull requests: for listing pull requests, reviews, comments and commits
    - Organization
      - Administration: for listing events from audit log
      - Members: for listing teams and their members

  NOTES:
    - We assume that ALL the repositories are going to be listed **should be owned by the organization, not the users**.
    - Enterprise Cloud is required for this connector.

  Apart from Github instructions please review the following:
  - "Homepage URL" can be anything, not required in this flow but required by GitHub.
  - Webhooks check can be disabled as this connector is not using them
  - Keep `Expire user authorization tokens` enabled, as GitHub documentation recommends
  3. Once is created please generate a new `Private Key`.
  4. It is required to convert the format of the certificate downloaded from PKCS#1 in previous step to PKCS#8. Please run following command:
```shell
openssl pkcs8 -topk8 -inform PEM -outform PEM -in {YOUR DOWNLOADED CERTIFICATE FILE} -out gh_pk_pkcs8.pem -nocrypt
```

**NOTES**:
 - If the certificate is not converted to PKCS#8 connector will NOT work. You might see in logs a Java error `Invalid PKCS8 data.` if the format is not correct.
 - Command proposed has been successfully tested on Ubuntu; it may differ for other operating systems.

  5. Install the application in your organization.
     Go to your organization settings and then in "Developer Settings". Then, click on "Edit" for your "Github App" and once you are in the app settings, click on "Install App" and click on the "Install" button. Accept the permissions to install it in your whole organization.
  6. Once installed, the `installationId` is required as it needs to be provided in the proxy as parameter for the connector in your Terraform module. You can go to your organization settings and
click on `Third Party Access`. Click on `Configure` the application you have installed in previous step and you will find the `installationId` at the URL of the browser:
```
https://github.com/organizations/{YOUR ORG}/settings/installations/{INSTALLATION_ID}
```
  Copy the value of `installationId` and assign it to the `github_installation_id` variable in Terraform. You will need to redeploy the proxy again if that value was not populated before.

**NOTE**:
 - If `github_installation_id` is not set, authentication URL will not be properly formatted and you will see *401: Unauthorized* when trying to get an access token.
 - If you see *404: Not found* in logs please review the *IP restriction policies* that your organization might have; that could cause connections from psoxy AWS Lambda/GCP Cloud Functions be rejected.

  7. Update the variables with values obtained in previous step:
     - `PSOXY_GITHUB_CLIENT_ID` with `App ID` value. **NOTE**: It should be `App Id` value as we are going to use authentication through the App and **not** *client_id*.
     - `PSOXY_GITHUB_PRIVATE_KEY` with content of the `gh_pk_pkcs8.pem` from previous step. You could open the certificate with VS Code or any other editor and copy all the content *as-is* into this variable.
  8. Once the certificate has been uploaded, please remove {YOUR DOWNLOADED CERTIFICATE FILE} and `gh_pk_pkcs8.pem` from your computer or store it in a safe place.

EOT
    }
    github-enterprise-server = {
      source_kind : "github-enterprise-server",
      availability : "ga",
      enable_by_default : false
      worklytics_connector_id : "github-enterprise-server-psoxy"
      display_name : "Github Enterprise Server"
      identifier_scope_id : "github"
      worklytics_connector_name : "Github Enterprise Server via Psoxy"
      target_host : local.github_enterprise_server_host
      source_auth_strategy : "oauth2_refresh_token"
      secured_variables : [
        {
          name : "ACCESS_TOKEN"
          writable : true
          sensitive : true
          value_managed_by_tf : false
        },
        {
          name : "REFRESH_TOKEN"
          writable : true
          sensitive : true
          value_managed_by_tf : false
        },
        {
          name : "OAUTH_REFRESH_TOKEN"
          writable : true
          lockable : true   # nonsensical; this parameter/secret IS the lock. it's really the tokens that should have lockable:true
          sensitive : false # not sensitive; this just represents lock of the refresh of the token, not hold token value itself
          value_managed_by_tf : false
        },
        {
          name : "CLIENT_ID"
          writable : false
          sensitive : true # not really, but simpler this way; and some may want it treated as sensitive, since would be req'd to brute-force app tokens or something
          value_managed_by_tf : false
        },
        {
          name : "CLIENT_SECRET"
          writable : false
          sensitive : true
          value_managed_by_tf : false
        },
      ],
      environment_variables : {
        GRANT_TYPE : "refresh_token"
        REFRESH_ENDPOINT : "https://${local.github_enterprise_server_host}/login/oauth/access_token"
        USE_SHARED_TOKEN : "TRUE"
      }
      settings_to_provide = {
        "GitHub Organization" = local.github_organization
      }
      reserved_concurrent_executions : null
      example_api_calls_user_to_impersonate : null
      example_api_calls : [
        "/api/${local.github_enterprise_server_version}/orgs/${local.github_first_organization}/repos",
        "/api/${local.github_enterprise_server_version}/orgs/${local.github_first_organization}/members",
        "/api/${local.github_enterprise_server_version}/orgs/${local.github_first_organization}/teams",
        "/api/${local.github_enterprise_server_version}/orgs/${local.github_first_organization}/audit-log",
        "/api/${local.github_enterprise_server_version}/repos/${local.github_first_organization}/${local.github_example_repository}/events",
        "/api/${local.github_enterprise_server_version}/repos/${local.github_first_organization}/${local.github_example_repository}/commits",
        "/api/${local.github_enterprise_server_version}/repos/${local.github_first_organization}/${local.github_example_repository}/issues",
        "/api/${local.github_enterprise_server_version}/repos/${local.github_first_organization}/${local.github_example_repository}/pulls",
      ]
      external_token_todo : <<EOT
You can use a [guided script](../../../tools/github-enterprise-server-auth.sh) to setup the connector. In any case, you can follow here the manual steps that needs to be done.

  1. You have to populate:
     - `github_enterprise_server_host` variable in Terraform with the hostname of your Github Enterprise Server (example: `github.your-company.com`).
This host should be accessible from the psoxy function, as the connector will need to reach it.
     - `github_organization` variable in Terraform with the name of your organization in Github Enterprise Server. You can put more than one, just split them in commas (example: `org1,org2`).
  2. From your organization, register a [GitHub App](https://docs.github.com/en/enterprise-server@3.11/apps/creating-github-apps/registering-a-github-app/registering-a-github-app#registering-a-github-app)
    with following permissions with **Read Only**:
    - Repository:
      - Contents: for reading commits and comments
      - Issues: for listing issues, comments, assignees, etc.
      - Metadata: for listing repositories and branches
      - Pull requests: for listing pull requests, reviews, comments and commits
    - Organization
      - Administration: for listing events from audit log
      - Members: for listing teams and their members

  NOTES:
    - We assume that ALL the repositories are going to be listed **should be owned by the organization, not the users**.

  Apart from Github instructions please review the following:
  - "Homepage URL" can be anything, not required in this flow but required by GitHub.
  - "Callback URL" can be anything, but we recommend something like `http://localhost` as we will need it for the redirect as part of the authentication.
  - Webhooks check can be disabled as this connector is not using them
  - Keep `Expire user authorization tokens` enabled, as GitHub documentation recommends
  3. Once is created please generate a new `Client Secret`.
  4. Copy the `Client ID` and copy in your browser following URL, replacing the `CLIENT_ID` with the value you have just copied:
```
https://${local.github_enterprise_server_host}/login/oauth/authorize?client_id={YOUR CLIENT ID}
```
  5. The browser will ask you to accept permissions and then it will redirect you with to the previous `Callback URL` set as part of the application.
The URL should look like this: `https://localhost/?code=69d0f5bd0d82282b9a11`.
  6. Copy the value of `code` and run the following URL replacing in the placeholders the values of `Client ID` and `Client Secret`:
```
curl --location --request POST 'https://${local.github_enterprise_server_host}/login/oauth/access_token?client_id={YOUR CLIENT ID}&client_secret={YOUR CLIENT SECRET}&code={YOUR CODE}' --header 'Content-Type: application/json' --header 'Accept: application/json'
```
The response will be something like:

```json
{
  "access_token":"ghu_...",
  "expires_in":28800,
  "refresh_token":"ghr_...",
  "refresh_token_expires_in":15724800,
  "token_type":"bearer",
  "scope":""
}
```
You will need to copy the value of the `refresh_token`.

**NOTES**:
 - `Code` can be used once, so if you need to repeat the process you will need to generate a new one.

  7. Update the variables with values obtained in previous step:
     - `psoxy_GITHUB_ENTERPRISE_SERVER_CLIENT_ID` with `Client Id` value.
     - `psoxy_GITHUB_ENTERPRISE_SERVER_CLIENT_SECRET` with `Client Secret` value.
     - `psoxy_GITHUB_ENTERPRISE_SERVER_REFRESH_TOKEN` with the `refresh_token`.

EOT
    }
    github-non-enterprise = {
      source_kind : "github",
      availability : "ga",
      enable_by_default : false
      worklytics_connector_id : "github-free-team-psoxy"
      display_name : "Github"
      identifier_scope_id : "github"
      worklytics_connector_name : "Github Free/Professional/Team via Psoxy"
      target_host : "api.github.com"
      source_auth_strategy : "oauth2_refresh_token"
      secured_variables : [
        {
          name : "ACCESS_TOKEN"
          writable : true
          sensitive : true
          value_managed_by_tf : false
        },
        {
          name : "PRIVATE_KEY"
          writable : false
          sensitive : true
          value_managed_by_tf : false
        },
        {
          name : "CLIENT_ID"
          writable : false
          sensitive : true
          value_managed_by_tf : false
        },
        {
          name : "OAUTH_REFRESH_TOKEN"
          writable : true
          lockable : true
          sensitive : true
          value_managed_by_tf : false
        }
      ],
      environment_variables : {
        GRANT_TYPE : "certificate_credentials"
        TOKEN_RESPONSE_TYPE : "GITHUB_ACCESS_TOKEN"
        REFRESH_ENDPOINT : "https://api.github.com/app/installations/${local.github_installation_id}/access_tokens"
        USE_SHARED_TOKEN : "TRUE"
      }
      settings_to_provide = {
        "GitHub Organization" = local.github_organization
      }
      reserved_concurrent_executions : null
      example_api_calls_user_to_impersonate : null
      example_api_calls : [
        "/orgs/${local.github_organization}/repos",
        "/orgs/${local.github_organization}/members",
        "/orgs/${local.github_organization}/teams",
        "/repos/${local.github_organization}/${local.github_example_repository}/events",
        "/repos/${local.github_organization}/${local.github_example_repository}/commits",
        "/repos/${local.github_organization}/${local.github_example_repository}/issues",
        "/repos/${local.github_organization}/${local.github_example_repository}/pulls",
      ]
      external_token_todo : <<EOT
  1. Populate `github_organization` variable in Terraform with the name of your GitHub organization.
  2. From your organization, register a [GitHub App](https://docs.github.com/en/apps/creating-github-apps/registering-a-github-app/registering-a-github-app#registering-a-github-app)
    with following permissions with **Read Only**:
    - Repository:
      - Contents: for reading commits and comments
      - Issues: for listing issues, comments, assignees, etc.
      - Metadata: for listing repositories and branches
      - Pull requests: for listing pull requests, reviews, comments and commits
    - Organization
      - Members: for listing teams and their members

  NOTES:
    - We assume that ALL the repositories are going to be listed **should be owned by the organization, not the users**.

  Apart from Github instructions please review the following:
  - "Homepage URL" can be anything, not required in this flow but required by GitHub.
  - Webhooks check can be disabled as this connector is not using them
  - Keep `Expire user authorization tokens` enabled, as GitHub documentation recommends
  3. Once is created please generate a new `Private Key`.
  4. It is required to convert the format of the certificate downloaded from PKCS#1 in previous step to PKCS#8. Please run following command:
```shell
openssl pkcs8 -topk8 -inform PEM -outform PEM -in {YOUR DOWNLOADED CERTIFICATE FILE} -out gh_pk_pkcs8.pem -nocrypt
```

**NOTES**:
 - If the certificate is not converted to PKCS#8 connector will NOT work. You might see in logs a Java error `Invalid PKCS8 data.` if the format is not correct.
 - Command proposed has been successfully tested on Ubuntu; it may differ for other operating systems.

  5. Install the application in your organization.
     Go to your organization settings and then in "Developer Settings". Then, click on "Edit" for your "Github App" and once you are in the app settings, click on "Install App" and click on the "Install" button. Accept the permissions to install it in your whole organization.
  6. Once installed, the `installationId` is required as it needs to be provided in the proxy as parameter for the connector in your Terraform module. You can go to your organization settings and
click on `Third Party Access`. Click on `Configure` the application you have installed in previous step and you will find the `installationId` at the URL of the browser:
```
https://github.com/organizations/{YOUR ORG}/settings/installations/{INSTALLATION_ID}
```
  Copy the value of `installationId` and assign it to the `github_installation_id` variable in Terraform. You will need to redeploy the proxy again if that value was not populated before.

**NOTE**:
 - If `github_installation_id` is not set, authentication URL will not be properly formatted and you will see *401: Unauthorized* when trying to get an access token.
 - If you see *404: Not found* in logs please review the *IP restriction policies* that your organization might have; that could cause connections from psoxy AWS Lambda/GCP Cloud Functions be rejected.

  7. Update the variables with values obtained in previous step:
     - `PSOXY_GITHUB_CLIENT_ID` with `App ID` value. **NOTE**: It should be `App Id` value as we are going to use authentication through the App and **not** *client_id*.
     - `PSOXY_GITHUB_PRIVATE_KEY` with content of the `gh_pk_pkcs8.pem` from previous step. You could open the certificate with VS Code or any other editor and copy all the content *as-is* into this variable.
  8. Once the certificate has been uploaded, please remove {YOUR DOWNLOADED CERTIFICATE FILE} and `gh_pk_pkcs8.pem` from your computer or store it in a safe place.

EOT
    }
    salesforce = {
      source_kind : "salesforce",
      availability : "ga",
      enable_by_default : false
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
        ACCESS_TOKEN_CACHEABLE : "true",
        USE_SHARED_TOKEN : "true"
      }
      secured_variables : [
        {
          name : "CLIENT_SECRET"
          writable : false
          sensitive : true
          value_managed_by_tf : false
        },
        {
          name : "CLIENT_ID"
          writable : false
          sensitive : true
          value_managed_by_tf : false
        },
        {
          name : "OAUTH_REFRESH_TOKEN"
          writable : true
          lockable : true
          sensitive : true
          value_managed_by_tf : false
        },
        {
          name : "ACCESS_TOKEN"
          writable : true
          sensitive : true
          value_managed_by_tf : false
        },
      ]
      reserved_concurrent_executions : null
      example_api_calls_user_to_impersonate : null
      example_api_calls : [
        "/services/data/v57.0/sobjects/Account/describe",
        "/services/data/v57.0/sobjects/ActivityHistory/describe",
        "/services/data/v57.0/sobjects/Account/updated?start=${urlencode(timeadd(time_static.deployment.id, "-48h"))}&end=${urlencode(time_static.deployment.id)}",
        "/services/data/v57.0/composite/sobjects/User?ids=${local.salesforce_example_account_id}&fields=Alias,AccountId,ContactId,CreatedDate,CreatedById,Email,EmailEncodingKey,Id,IsActive,LastLoginDate,LastModifiedDate,ManagerId,Name,TimeZoneSidKey,Username,UserRoleId,UserType",
        "/services/data/v57.0/composite/sobjects/Account?ids=${local.salesforce_example_account_id}&fields=Id,AnnualRevenue,CreatedDate,CreatedById,IsDeleted,LastActivityDate,LastModifiedDate,LastModifiedById,NumberOfEmployees,OwnerId,ParentId,Rating,Sic,Type",
        "/services/data/v57.0/query?q=SELECT%20%28SELECT%20AccountId%2CActivityDate%2CActivityDateTime%2CActivitySubtype%2CActivityType%2CCallDurationInSeconds%2CCallType%2CCreatedDate%2CCreatedById%2CDurationInMinutes%2CEndDateTime%2CId%2CIsAllDayEvent%2CIsDeleted%2CIsHighPriority%2CIsTask%2CLastModifiedDate%2CLastModifiedById%2COwnerId%2CPriority%2CStartDateTime%2CStatus%2CWhatId%2CWhoId%20FROM%20ActivityHistories%20ORDER%20BY%20LastModifiedDate%20DESC%20NULLS%20LAST%29%20FROM%20Account%20where%20id%3D%27${local.salesforce_example_account_id}%27",
        "/services/data/v57.0/query?q=SELECT+Alias,AccountId,ContactId,CreatedDate,CreatedById,Email,EmailEncodingKey,Id,IsActive,LastLoginDate,LastModifiedDate,ManagerId,Name,TimeZoneSidKey,Username,UserRoleId,UserType+FROM+User+WHERE+LastModifiedDate+%3E%3D+${urlencode(timeadd(time_static.deployment.id, "-72h"))}+AND+LastModifiedDate+%3C+${urlencode(time_static.deployment.id)}+ORDER+BY+LastModifiedDate+DESC+NULLS+LAST",
        "/services/data/v57.0/query?q=SELECT+Id,AnnualRevenue,CreatedDate,CreatedById,IsDeleted,LastActivityDate,LastModifiedDate,LastModifiedById,NumberOfEmployees,OwnerId,ParentId,Rating,Sic,Type+FROM+Account+WHERE+LastModifiedDate+%3E%3D+${urlencode(timeadd(time_static.deployment.id, "-72h"))}+AND+LastModifiedDate+%3C+${urlencode(time_static.deployment.id)}+ORDER+BY+LastModifiedDate+DESC+NULLS+LAST"
      ]
      external_token_todo : <<EOT
  Before running the example, you have to populate the following variables in terraform:
  - `salesforce_domain`. This is the [domain](https://help.salesforce.com/s/articleView?id=sf.faq_domain_name_what.htm&type=5) your instance is using.
  - `salesforce_example_account_id`: An example of any account id; this is only applicable for example calls.

  1. Create a [Salesforce application + client credentials flow](https://help.salesforce.com/s/articleView?language=en_US&id=sf.remoteaccess_oauth_client_credentials_flow.htm&type=5)
    with following permissions:
    - Manage user data via APIs (`api`)
    - Access Connect REST API resources (`chatter_api`)
    - Perform requests at any time (`refresh_token`, `offline_access`)
    - Access unique user identifiers (`openid`)
    - Access Lightning applications (`lightning`)
    - Access content resources (`content`)
    - Perform ANSI SQL queries on Customer Data Platform data (`cdp_query_api`)

     Apart from Salesforce instructions above, please review the following:
     - "Callback URL" MUST be filled; can be anything as not required in this flow, but required to be set by Salesforce.
     - Application MUST be marked with "Enable Client Credentials Flow"
     - You MUST assign a user for Client Credentials, be sure:
        - you associate a "run as" user marked with "API Only Permission"
        - The policy associated to the user MUST have the following Administrative Permissions enabled:
          - API Enabled
          - APEX REST Services
      - The policy MUST have the application marked as "enabled" in "Connected App Access". Otherwise requests will return 401 with INVALID_SESSION_ID

     The user set for "run as" on the connector should have, between its `Permission Sets` and `Profile`, the permission of `View All Data`. This is required
     to support the queries used to retrieve [Activity Histories](https://developer.salesforce.com/docs/atlas.en-us.object_reference.meta/object_reference/sforce_api_objects_activityhistory.htm) by *account id*.

  2. Once created, open "Manage Consumer Details"
  3. Update the content of `PSOXY_SALESFORCE_CLIENT_ID` from Consumer Key	and `PSOXY_SALESFORCE_CLIENT_SECRET` from Consumer Secret
  4. Finally, we recommend to run `test-salesforce` script with all the queries in the example to ensure the expected information covered by rules can be obtained from Salesforce API.
     Some test calls may fail with a 400 (bad request) response. That is something expected if parameters requested on the query are not available (for example, running a SOQL query
     with fields that are NOT present in your model will force a 400 response from Salesforce API). If that is the case, a double check in the function logs can be done to ensure
     that this is the actual error happening, you should see an error like the following one:
     ```json
     WARNING: Source API Error [{
    "message": "\nLastModifiedById,NumberOfEmployees,OwnerId,Ownership,ParentId,Rating,Sic,Type\n
               ^\nERROR at Row:1:Column:136\nNo such column 'Ownership' on entity 'Account'. If you are attempting to use a custom field, be sure to append the '__c' after the custom field name. Please reference your WSDL or the describe call for the appropriate names.",
    "errorCode": "INVALID_FIELD"
     }]
     ```
     In that case, removing from the query the fields LastModifiedById,NumberOfEmployees,OwnerId,Ownership,ParentId,Rating,Sic,Type will fix the issues.

     However, if running any of the queries you receive a 401/403/500/512. A 401/403 it might be related to some misconfiguration in the Salesforce Application due lack of permissions;
     a 500/512 it could be related to missing parameter in the function configuration (for example, a missing value for `salesforce_domain` variable in your terraform vars)
EOT
    }
    slack-discovery-api = {
      source_kind : "slack"
      availability : "ga",
      enable_by_default : true
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
        {
          name : "ACCESS_TOKEN"
          writable : false
          sensitive : true
          value_managed_by_tf : false
        },
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
      availability : "ga",
      enable_by_default : true
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
        {
          name : "CLIENT_SECRET"
          writable : false
          sensitive : true
          value_managed_by_tf : false
          description : "Client Secret of the Zoom 'Server-to-Server' OAuth App used by the Connector to retrieve Zoom data.  Value should be obtained from your Zoom admin."
        },
        {
          name : "CLIENT_ID"
          writable : false
          sensitive : false
          value_managed_by_tf : false
          description : "Client ID of the Zoom 'Server-to-Server' OAuth App used by the Connector to retrieve Zoom data. Value should be obtained from your Zoom admin."
        },
        {
          name : "ACCOUNT_ID"
          writable : false
          sensitive : true
          value_managed_by_tf : false
          description : "Account ID of the Zoom tenant from which the Connector will retrieve Zoom data. Value should be obtained from your Zoom admin."
        },
        {
          name : "ACCESS_TOKEN"
          writable : true
          sensitive : true
          value_managed_by_tf : false
          description : "Short-lived Oauth access_token used by connector to retrieve Zoom data. Filled by Proxy instance."
        },
        {
          name : "OAUTH_REFRESH_TOKEN"
          writable : true
          lockable : true
          sensitive : true
          value_managed_by_tf : false
        }, # q: needed? per logic as of 9 June 2023, would be created
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
The Zoom connector through Psoxy requires a Custom Managed App on the Zoom Marketplace. This app may
be left in development mode; it does not need to be published.

1. Go to https://marketplace.zoom.us/develop/create and create an app of type "Server to Server
   OAuth" for creating a server-to-server app.

2. After creation, it will show the App Credentials.

   Copy the following values:

   - `Account ID`
   - `Client ID`
   - `Client Secret`

   Share them with the AWS/GCP administrator, who should fill them in your host platform's secret
   manager (AWS Systems Manager Parameter Store / GCP Secret Manager) for use by the proxy when
   authenticating with the Zoom API:

   - `Account ID` --> `PSOXY_ZOOM_ACCOUNT_ID`
   - `Client ID` --> `PSOXY_ZOOM_CLIENT_ID`
   - `Client Secret` --> `PSOXY_ZOOM_CLIENT_SECRET`

   NOTE: Anytime the _Client Secret_ is regenerated it needs to be updated in the Proxy too. NOTE:
   _Client Secret_ should be handled according to your organization's security policies for API
   keys/secrets as, in combination with the above, allows access to your organization's data.

3. Fill the 'Information' section. Zoom requires company name, developer name, and developer email
   to activate the app.

4. No changes are needed in the 'Features' section. Continue.

5. Fill the scopes section clicking on `+ Add Scopes` and adding the following:

    * `meeting:read:past_meeting:admin`
    * `meeting:read:meeting:admin`
    * `meeting:read:list_past_participants:admin`
    * `meeting:read:list_past_instances:admin`
    * `meeting:read:list_meetings:admin`
    * `meeting:read:participant:admin`
    * `report:read:list_meeting_participants:admin`
    * `report:read:meeting:admin`
    * `report:read:user:admin`
    * `user:read:user:admin`
    * `user:read:list_users:admin`

  Alternatively, the scopes: `user:read:admin`, `meeting:read:admin`, `report:read:admin` are
  sufficient, but as of May 2024 are no longer available for newly created Zoom apps.

  Once the scopes are added, click on `Done` and then `Continue`.

6. Activate the app

EOT
    },
    dropbox-business = {
      source_kind : "dropbox-business"
      availability : "deprecated",
      enable_by_default : false
      worklytics_connector_id : "dropbox-business-log-psoxy"
      target_host : "api.dropboxapi.com"
      source_auth_strategy : "oauth2_refresh_token"
      display_name : "Dropbox Business"
      identifier_scope_id : "dropbox-business"
      worklytics_connector_name : "Dropbox Business via Psoxy"
      secured_variables : [
        {
          name : "REFRESH_TOKEN"
          writable : false
          sensitive : true
          value_managed_by_tf : false
        },
        {
          name : "CLIENT_ID"
          writable : false
          sensitive : true
          value_managed_by_tf : false
        },
        {
          name : "CLIENT_SECRET"
          writable : false
          sensitive : true
          value_managed_by_tf : false
        },
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
      availability : "ga",
      enable_by_default : false
      worklytics_connector_id : "jira-server-psoxy"
      target_host : var.jira_server_url
      source_auth_strategy : "oauth2_access_token"
      display_name : "Jira Data Center"
      identifier_scope_id : "jira"
      worklytics_connector_name : "Jira Server REST API via Psoxy"
      secured_variables : [
        {
          name : "ACCESS_TOKEN"
          writable : false
          sensitive : true
          value_managed_by_tf : false
        },
      ],
      reserved_concurrent_executions : null
      example_api_calls_user_to_impersonate : null
      example_api_calls : [
        "/rest/api/2/search?maxResults=25",
        "/rest/api/2/issue/${local.jira_example_issue_id}/comment?maxResults=25",
        "/rest/api/2/issue/${local.jira_example_issue_id}/worklog?maxResults=25",
        "/rest/api/latest/search?maxResults=25",
        "/rest/api/latest/issue/${local.jira_example_issue_id}/comment?maxResults=25",
        "/rest/api/latest/issue/${local.jira_example_issue_id}/worklog?maxResults=25",
      ],
      external_token_todo : <<EOT
  1. Follow the instructions to create a [Personal Access Token](https://confluence.atlassian.com/enterprise/using-personal-access-tokens-1026032365.html) in your instance.
     As this is coupled to a specific User in Jira, we recommend first creating a dedicated Jira user
     to be a "Service Account" in effect for the connection (name it `svc-worklytics` or something).
     This will give you better visibility into activity of the data connector as well as avoid
     connection inadvertently breaking if the Jira user who owns the token is disabled or deleted.

     That service account must have *READ* permissions over your Jira instance, to be able to read issues, worklogs and comments, including their changelog where possible.
     If you're required to specify a classical scope, you can add:
     - `read:jira-work`
  2. Disable or set a reasonable expiration time for the token. If you set an expiration time, it is your responsibility to re-generate the token and reset it in your host environment to maintain your connection.
  3. Copy the value of the token in `PSOXY_JIRA_SERVER_ACCESS_TOKEN` variable as part of AWS System
     Manager Parameter Store / GCP Cloud Secrets.
EOT
    }
    jira-cloud = {
      source_kind : "jira-cloud"
      availability : "ga"
      enable_by_default : true
      worklytics_connector_id : "jira-cloud-psoxy"
      target_host : "api.atlassian.com"
      source_auth_strategy : "oauth2_refresh_token"
      display_name : "Jira REST API"
      identifier_scope_id : "jira"
      worklytics_connector_name : "Jira REST API via Psoxy"
      secured_variables : [
        {
          name : "ACCESS_TOKEN"
          writable : true
          sensitive : true
          value_managed_by_tf : false
        },
        {
          name : "REFRESH_TOKEN"
          writable : true
          sensitive : true
          value_managed_by_tf : false
        },
        {
          name : "OAUTH_REFRESH_TOKEN"
          writable : true
          lockable : true   # nonsensical; this parameter/secret IS the lock. it's really the tokens that should have lockable:true
          sensitive : false # not sensitive; this just represents lock of the refresh of the token, not hold token value itself
          value_managed_by_tf : false
        },
        {
          name : "CLIENT_ID"
          writable : false
          sensitive : true # not really, but simpler this way; and some may want it treated as sensitive, since would be req'd to brute-force app tokens or something
          value_managed_by_tf : false
        },
        {
          name : "CLIENT_SECRET"
          writable : false
          sensitive : true
          value_managed_by_tf : false
        }
      ],
      environment_variables : {
        GRANT_TYPE : "refresh_token"
        REFRESH_ENDPOINT : "https://auth.atlassian.com/oauth/token"
        USE_SHARED_TOKEN : "TRUE"
      }
      settings_to_provide = {
      }
      reserved_concurrent_executions : null
      example_api_calls_user_to_impersonate : null
      example_api_calls : [
        "/oauth/token/accessible-resources", # obtain Jira Cloud ID from here
        "/ex/jira/${local.jira_example_cloud_id}/rest/api/2/users",
        "/ex/jira/${local.jira_example_cloud_id}/rest/api/2/users",
        "/ex/jira/${local.jira_example_cloud_id}/rest/api/2/group/bulk",
        "/ex/jira/${local.jira_example_cloud_id}/rest/api/2/search?maxResults=25",
        "/ex/jira/${local.jira_example_cloud_id}/rest/api/2/issue/${local.jira_example_issue_id}/changelog?maxResults=25",
        "/ex/jira/${local.jira_example_cloud_id}/rest/api/2/issue/${local.jira_example_issue_id}/comment?maxResults=25",
        "/ex/jira/${local.jira_example_cloud_id}/rest/api/2/issue/${local.jira_example_issue_id}/worklog?maxResults=25",
        "/ex/jira/${local.jira_example_cloud_id}/rest/api/3/users",
        "/ex/jira/${local.jira_example_cloud_id}/rest/api/3/group/bulk",
        "/ex/jira/${local.jira_example_cloud_id}/rest/api/3/search?maxResults=25",
        "/ex/jira/${local.jira_example_cloud_id}/rest/api/3/issue/${local.jira_example_issue_id}/changelog?maxResults=25",
        "/ex/jira/${local.jira_example_cloud_id}/rest/api/3/issue/${local.jira_example_issue_id}/comment?maxResults=25",
        "/ex/jira/${local.jira_example_cloud_id}/rest/api/3/issue/${local.jira_example_issue_id}/worklog?maxResults=25",
        "/ex/jira/${local.jira_example_cloud_id}/rest/api/3/project/search?maxResults=25",
      ],
      external_token_todo : <<EOT
## Prerequisites
[Jira OAuth 2.0 (3LO)](https://developer.atlassian.com/cloud/jira/platform/oauth-2-3lo-apps/) through Psoxy requires a Jira Cloud account with following classical scopes:

- read:jira-user: for getting generic user information
- read:jira-work: for getting information about issues, comments, etc

And following granular scopes:
- read:account: for getting user emails
- read:group:jira: for retrieving group members
- read:avatar:jira: for retrieving group members

## Setup Instructions
  1. Go to https://developer.atlassian.com/console/myapps/ and click on "Create" and choose "OAuth 2.0 Integration"

  2. Then click "Authorization" and "Add" on `OAuth 2.0 (3L0)`, adding `http://localhost` as callback URI. It can be any URL
     that matches the URL format and it is required to be populated, but the proxy instance workflow will not use it.

  3. Now navigate to "Permissions" and click on "Add" for `Jira API`. Once added, click on "Configure".
     Add following scopes as part of "Classic Scopes", first clicking on `Edit Scopes` and then selecting them:
       - `read:jira-user`
       - `read:jira-work`
     And these from "Granular Scopes":
       - `read:group:jira`
       - `read:avatar:jira`
       - `read:user:jira`
     Then go back to "Permissions" and click on "Add" for `User Identity API`, only selecting following scopes:
       - `read:account`

     After adding all the scopes, you should have 1 permission for `User Identity API` and 5 for `Jira API`.

  4. Once Configured, go to "Settings" and copy the "Client Id" and "Secret". You will use these to
     obtain an OAuth `refresh_token`.

  5. Build an OAuth authorization endpoint URL by copying the value for "Client Id" obtained in the
    previous step into the URL below. Then open the result in a web browser:

   `https://auth.atlassian.com/authorize?audience=api.atlassian.com&client_id=<CLIENT ID>&scope=offline_access%20read:group:jira%20read:avatar:jira%20read:user:jira%20read:account%20read:jira-user%20read:jira-work&redirect_uri=http://localhost&state=YOUR_USER_BOUND_VALUE&response_type=code&prompt=consent`

     NOTES:
     - That URL can be obtained from "Authorization" and clicking on `Configure` for  "OAuth 2.0 (3LO)" page.
     - If after pasting the URL you see an error page (like an issue with identity provider) please check:
       1 - The `clientId` is correct
       2 - The `redirect_uri` is the same as the one configured in the "OAuth 2.0 (3LO)" page
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

 10. Obtain the "Cloud ID" of your Jira instance. Use the following command, with the
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
  proper value for targeting a valid Jira Cloud instance.
EOT
    }
  }

  bulk_connectors = {
    "badge" = {
      source_kind               = "badge"
      availability              = "ga"
      enable_by_default         = true
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
        "Parser" = "badge"
      }
      example_file = "docs/sources/badge/badge-example.csv"
    }
    "hris" = {
      source_kind               = "hris"
      availability              = "ga"
      enable_by_default         = true
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
      example_file = "docs/sources/hris/hris-example.csv"
    }
    "survey" = {
      worklytics_connector_id   = "survey-import-psoxy"
      availability              = "ga"
      enable_by_default         = true
      source_kind               = "survey"
      worklytics_connector_name = "Survey Data Import via Psoxy"
      rules = {
        columnsToRedact = []
        columnsToPseudonymize = [
          "EMPLOYEE_ID", # primary key
          # "EMPLOYEE_EMAIL", # if exists
        ]
      }
      example_file = "docs/sources/survey/survey-example.csv"
    }
    "qualtrics" = {
      source_kind               = "qualtrics"
      availability              = "beta"
      enable_by_default         = false
      worklytics_connector_id   = "survey-import-psoxy"
      worklytics_connector_name = "Survey Data Import via Psoxy"
      rules = {
        columnsToRedact = []
        columnsToPseudonymize = [
          "EMPLOYEE_ID", # primary key
          # "employee_email", # if exists
        ]
      }
      example_file = "docs/sources/survey/survey-example.csv"
    }
  }

  oauth_long_access_connectors_backwards = { for k, v in local.oauth_long_access_connectors :
  k => merge(v, { example_calls : v.example_api_calls }) }


  all_default_connectors = merge(
    local.google_workspace_sources,
    local.msft_365_connectors,
    local.oauth_long_access_connectors,
    local.bulk_connectors,
  )

  default_ga_connectors = {
    for k, v in local.all_default_connectors : k => v if(
      v.availability == "ga"
      && v.enable_by_default
      # either GWS included, or NOT a gws-connector
      && (var.include_google_workspace || !contains(keys(local.google_workspace_sources), k))
      # either MSFT include or NOT a msft-connector
      && (var.include_msft || !contains(keys(local.msft_365_connectors), k))
    )
  }

  # to expose via console
  # eg, `echo "local.available_connector_ids" | terraform console` will print this
  # used via `tools/init-tfvars.sh` script, as default values for `enabled_connectors` variable
  default_enabled_connector_ids = keys(local.default_ga_connectors)
}

# computed values filtered by enabled connectors
locals {
  enabled_google_workspace_connectors = {
    for k, v in local.google_workspace_sources_backwards : k => v if contains(var.enabled_connectors, k)
  }
  enabled_msft_365_connectors = {
    for k, v in local.msft_365_connectors_backwards : k => v if contains(var.enabled_connectors, k) && length(try(var.msft_tenant_id, "")) > 0
  }
  enabled_oauth_long_access_connectors = { for k, v in local.oauth_long_access_connectors_backwards : k => v if contains(var.enabled_connectors, k) }

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