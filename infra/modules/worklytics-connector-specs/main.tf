
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
  standard_config_values = {
    oauth_refresh_token_lock = {
      # NOTE: in GCP case, this is NEVER actually filled with a value; lock is done by labeling the secret
      name : "OAUTH_REFRESH_TOKEN"
      writable : true
      lockable : true   # nonsensical; this parameter/secret IS the lock. it's really the tokens that should have lockable:true
      sensitive : false # not sensitive; this just represents lock of the refresh of the token, not hold token value itself # NO, as of 2025, AWS also seems to be using a separate one with 'lock' suffix
      value_managed_by_tf : false
      description : "Used to 'lock' the token refresh flow, so multiple processes don't refresh tokens concurrently. Filled by Proxy instance. Not sensitive."
    }
  }

  # 3 days before the sample date, for interesting API calls (without repeating computation a dozen times)
  example_api_calls_sample_interval_start = timeadd(var.example_api_calls_sample_date, "-72h")

  chat_gpt_enterprise_example_workspace_id = coalesce(var.chat_gpt_enterprise_example_workspace_id, "YOUR_WORKSPACEID")
  confluence_example_cloud_id              = coalesce(var.confluence_cloud_id, "YOUR_CONFLUENCE_CLOUD_ID")
  jira_example_cloud_id                    = coalesce(var.jira_cloud_id, "YOUR_JIRA_CLOUD_ID")
  jira_example_issue_id                    = coalesce(var.jira_example_issue_id, var.example_jira_issue_id, "YOUR_JIRA_EXAMPLE_ISSUE_ID")
  github_installation_id                   = coalesce(var.github_installation_id, "YOUR_GITHUB_INSTALLATION_ID")
  github_copilot_installation_id           = coalesce(var.github_copilot_installation_id, "YOUR_GITHUB_COPILOT_INSTALLATION_ID")
  github_enterprise_server_host            = coalesce(var.github_api_host, var.github_enterprise_server_host, "YOUR_GITHUB_ENTERPRISE_SERVER_HOST")
  github_enterprise_server_version         = coalesce(var.github_enterprise_server_version, "v3")
  github_organization                      = coalesce(var.github_organization, "YOUR_GITHUB_ORGANIZATION_NAME")
  github_first_organization                = split(",", coalesce(var.github_organization, "YOUR_GITHUB_ORGANIZATION_NAME"))[0]
  github_example_repository                = coalesce(var.github_example_repository, "YOUR_GITHUB_EXAMPLE_REPOSITORY_NAME")
  salesforce_example_account_id            = coalesce(var.salesforce_example_account_id, "{ANY ACCOUNT ID}")

  oauth_long_access_connectors = {
    asana = {
      source_kind : "asana",
      availability : "ga",
      enable_by_default : false,
      worklytics_connector_id : "asana-psoxy"
      display_name : "Asana"
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
      enable_async_processing : false
      enable_side_output : false
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
    chatgpt-enterprise = {
      source_kind : "chatgpt-enterprise",
      availability : "alpha",
      enable_by_default : false,
      worklytics_connector_id : "chatgpt-enterprise-psoxy"
      display_name : "ChatGPT Enterprise"
      worklytics_connector_name : "ChatGPT Enterprise via Psoxy"
      target_host : "api.chatgpt.com"
      source_auth_strategy : "oauth2_access_token"
      secured_variables : [
        {
          name : "ACCESS_TOKEN" # ChatGPT's UX calls this an 'API Key', but it's actually an access token;
          writable : false
          sensitive : true
          value_managed_by_tf : false
        }
      ],
      settings_to_provide = {
        "Workspace Id" = local.chat_gpt_enterprise_example_workspace_id
      }
      reserved_concurrent_executions : null
      enable_async_processing : false
      enable_side_output : false
      example_api_calls_user_to_impersonate : null
      example_api_calls : [
        "/v1/compliance/workspaces/${local.chat_gpt_enterprise_example_workspace_id}/projects",
        "/v1/compliance/workspaces/${local.chat_gpt_enterprise_example_workspace_id}/conversations",
        "/v1/compliance/workspaces/${local.chat_gpt_enterprise_example_workspace_id}/automations",
      ]
      external_token_todo : templatefile("${path.module}/docs/chatgpt/enterprise/instructions.tftpl", {
        workspace_id                = local.chat_gpt_enterprise_example_workspace_id,
        path_to_instance_parameters = "PSOXY_CHATGPT_ENTERPRISE_"
      })
    }
    cursor = {
      source_kind : "cursor",
      availability : "alpha",
      enable_by_default : false,
      worklytics_connector_id : "cursor-psoxy"
      display_name : "Cursor"
      worklytics_connector_name : "Cursor via Psoxy"
      target_host : "api.cursor.com"
      source_auth_strategy : "basic_auth" # cursor API uses basic auth (RFC 7617 Section 2, with API key as 'user-id' and no password
      secured_variables : [
        {
          name : "BASIC_AUTH_USER_ID" # cursor's UX calls this an 'API Key', but it's actually a Basic Auth 'user-id'; should we have aliases or something?
          writable : false
          sensitive : true
          value_managed_by_tf : false
        }
      ],
      example_api_requests : [
        {
          method = "GET"
          path   = "/teams/members"
        },
        {
          method = "POST"
          path   = "/teams/daily-usage-data"
          body = jsonencode({
            startDate = (time_static.deployment.unix - 86400 * 30) - 1000,
            endDate   = time_static.deployment.unix * 1000
          })
        },
        {
          method = "POST"
          path   = "/teams/filtered-usage-events"
          body = jsonencode({
            startDate = (time_static.deployment.unix - 86400 * 30) - 1000,
            endDate   = time_static.deployment.unix * 1000
          })
        }
      ]
      external_token_todo : templatefile("${path.module}/docs/cursor/instructions.tftpl", {
        path_to_instance_parameters = "PSOXY_CURSOR_"
      })
    }
    github = {
      source_kind : "github",
      availability : "ga",
      enable_by_default : false,
      worklytics_connector_id : "github-enterprise-psoxy"
      display_name : "Github Enterprise"
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
        local.standard_config_values.oauth_refresh_token_lock,
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
      enable_async_processing : false
      enable_side_output : false
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
      external_token_todo : templatefile("${path.module}/docs/github/enterprise-cloud-instructions.tftpl", {
        github_organization         = local.github_organization,
        path_to_instance_parameters = "PSOXY_GITHUB_"
      })
      # q: what to do with this?  unlike other general template case, it requires `github_organization` to be set somehow ...
      # instructions_template = "${path.module}/docs/github/enterprise-cloud-instructions.tftpl"
    }
    github-copilot = {
      source_kind : "github-copilot",
      availability : "alpha",
      enable_by_default : false,
      worklytics_connector_id : "github-copilot-psoxy"
      display_name : "Github Copilot"
      worklytics_connector_name : "Github Copilot via Psoxy"
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
        local.standard_config_values.oauth_refresh_token_lock,
      ],
      environment_variables : {
        GRANT_TYPE : "certificate_credentials"
        TOKEN_RESPONSE_TYPE : "GITHUB_ACCESS_TOKEN"
        REFRESH_ENDPOINT : "https://api.github.com/app/installations/${local.github_copilot_installation_id}/access_tokens"
        USE_SHARED_TOKEN : "TRUE"
      }
      settings_to_provide = {
        "GitHub Organization" = local.github_organization
      }
      reserved_concurrent_executions : null
      enable_async_processing : false
      enable_side_output : false
      example_api_calls_user_to_impersonate : null
      example_api_calls : [
        "/orgs/${local.github_organization}/members",
        "/orgs/${local.github_organization}/teams",
        "/orgs/${local.github_organization}/audit-log",
        "/orgs/${local.github_organization}/copilot/billing/seats"
      ]
      external_token_todo : templatefile("${path.module}/docs/github/copilot-instructions.tftpl", {
        github_organization         = local.github_organization,
        path_to_instance_parameters = "PSOXY_GITHUB_COPILOT_"
      })
    }
    github-enterprise-server = {
      source_kind : "github-enterprise-server",
      availability : "ga",
      enable_by_default : false
      worklytics_connector_id : "github-enterprise-server-psoxy"
      display_name : "GitHub Enterprise Server"
      worklytics_connector_name : "GitHub Enterprise Server via Psoxy"
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
        local.standard_config_values.oauth_refresh_token_lock,
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
      enable_side_output : false
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
      external_token_todo : templatefile("${path.module}/docs/github/enterprise-server-instructions.tftpl", {
        github_enterprise_server_host = local.github_enterprise_server_host,
        path_to_instance_parameters   = "PSOXY_GITHUB_ENTERPRISE_SERVER_"
      })
    }
    github-non-enterprise = {
      source_kind : "github-non-enterprise",
      availability : "ga",
      enable_by_default : false
      worklytics_connector_id : "github-free-team-psoxy"
      display_name : "GitHub"
      worklytics_connector_name : "GitHub Free/Professional/Team via Psoxy"
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
        local.standard_config_values.oauth_refresh_token_lock
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
      enable_side_output : false
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
      external_token_todo : templatefile("${path.module}/docs/github/non-enterprise-cloud-instructions.tftpl", {
        github_organization         = local.github_organization,
        path_to_instance_parameters = "PSOXY_GITHUB_NON_ENTERPRISE_"
      })
    }
    salesforce = {
      source_kind : "salesforce",
      availability : "ga",
      enable_by_default : false
      worklytics_connector_id : "salesforce-psoxy"
      display_name : "Salesforce"
      worklytics_connector_name : "Salesforce via Psoxy"
      target_host : var.salesforce_domain
      source_auth_strategy : "oauth2_refresh_token"
      environment_variables : {
        GRANT_TYPE : "client_credentials"
        CREDENTIALS_FLOW : "client_secret"
        REFRESH_ENDPOINT : "https://${var.salesforce_domain}/services/oauth2/token"
        ACCESS_TOKEN_CACHEABLE : "true",
        USE_SHARED_TOKEN : "TRUE"
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
        local.standard_config_values.oauth_refresh_token_lock,
        {
          name : "ACCESS_TOKEN"
          writable : true
          sensitive : true
          value_managed_by_tf : false
        },
      ]
      reserved_concurrent_executions : null
      enable_side_output : false
      example_api_calls_user_to_impersonate : null
      example_api_calls : [
        "/services/data/v57.0/sobjects/Account/describe",
        "/services/data/v57.0/sobjects/ActivityHistory/describe",
        "/services/data/v57.0/sobjects/Account/updated?start=${urlencode(timeadd(var.example_api_calls_sample_date, "-48h"))}&end=${urlencode(var.example_api_calls_sample_date)}",
        "/services/data/v57.0/composite/sobjects/User?ids=${local.salesforce_example_account_id}&fields=Alias,AccountId,ContactId,CreatedDate,CreatedById,Email,EmailEncodingKey,Id,IsActive,LastLoginDate,LastModifiedDate,ManagerId,Name,TimeZoneSidKey,Username,UserRoleId,UserType",
        "/services/data/v57.0/composite/sobjects/Account?ids=${local.salesforce_example_account_id}&fields=Id,AnnualRevenue,CreatedDate,CreatedById,IsDeleted,LastActivityDate,LastModifiedDate,LastModifiedById,NumberOfEmployees,OwnerId,ParentId,Rating,Sic,Type",
        "/services/data/v57.0/query?q=SELECT%20%28SELECT%20AccountId%2CActivityDate%2CActivityDateTime%2CActivitySubtype%2CActivityType%2CCallDurationInSeconds%2CCallType%2CCreatedDate%2CCreatedById%2CDurationInMinutes%2CEndDateTime%2CId%2CIsAllDayEvent%2CIsDeleted%2CIsHighPriority%2CIsTask%2CLastModifiedDate%2CLastModifiedById%2COwnerId%2CPriority%2CStartDateTime%2CStatus%2CWhatId%2CWhoId%20FROM%20ActivityHistories%20ORDER%20BY%20LastModifiedDate%20DESC%20NULLS%20LAST%29%20FROM%20Account%20where%20id%3D%27${local.salesforce_example_account_id}%27",
        "/services/data/v57.0/query?q=SELECT+Alias,AccountId,ContactId,CreatedDate,CreatedById,Email,EmailEncodingKey,Id,IsActive,LastLoginDate,LastModifiedDate,ManagerId,Name,TimeZoneSidKey,Username,UserRoleId,UserType+FROM+User+WHERE+LastModifiedDate+%3E%3D+${urlencode(timeadd(var.example_api_calls_sample_date, "-72h"))}+AND+LastModifiedDate+%3C+${urlencode(var.example_api_calls_sample_date)}+ORDER+BY+LastModifiedDate+DESC+NULLS+LAST",
        "/services/data/v57.0/query?q=SELECT+Id,AnnualRevenue,CreatedDate,CreatedById,IsDeleted,LastActivityDate,LastModifiedDate,LastModifiedById,NumberOfEmployees,OwnerId,ParentId,Rating,Sic,Type+FROM+Account+WHERE+LastModifiedDate+%3E%3D+${urlencode(timeadd(var.example_api_calls_sample_date, "-72h"))}+AND+LastModifiedDate+%3C+${urlencode(var.example_api_calls_sample_date)}+ORDER+BY+LastModifiedDate+DESC+NULLS+LAST"
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
    # https://api.slack.com/methods/admin.analytics.getFile
    slack-analytics = {
      source_kind : "slack-analytics",
      availability : "alpha",
      enable_by_default : false
      worklytics_connector_id : "slack-analytics-psoxy"
      worklytics_connector_name : "Slack Analytics via Psoxy"
      display_name : "Slack Analytics via Psoxy"
      target_host : "www.slack.com"
      source_auth_strategy : "oauth2_access_token"
      oauth_scopes_needed : [
        "admin.analytics:read",
      ]
      enable_async_processing : true
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
      enable_side_output : false
      example_api_calls_user_to_impersonate : null
      example_api_calls : [
        "/api/admin.analytics.getFile?type=member&date=${urlencode(formatdate("YYYY-MM-DD", var.example_api_calls_sample_date))}"
      ]
      instructions_template = "${path.module}/docs/slack/analytics/instructions.tftpl"
      external_token_todo : templatefile("${path.module}/docs/slack/analytics/instructions.tftpl", {
        path_to_instance_parameters = "PSOXY_SLACK_ANALYTICS_"
      })
    }
    slack-discovery-api = {
      source_kind : "slack"
      availability : "ga",
      enable_by_default : true
      worklytics_connector_id : "slack-discovery-api-psoxy",
      worklytics_connector_name : "Slack via Psoxy",
      display_name : "Slack via Discovery API"
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
      enable_side_output : false
      example_api_calls_user_to_impersonate : null
      example_api_calls : [
        "/api/discovery.enterprise.info?include_deleted=false&limit=5",
        "/api/discovery.conversations.list?limit=10",
        "/api/discovery.conversations.info?team={WORKSPACE_ID}&channel={CHANNEL_ID}",
        "/api/discovery.conversations.recent?limit=10",
        "/api/discovery.conversations.history?reactions=1&team={WORKSPACE_ID}&channel={CHANNEL_ID}&limit=10",
        "/api/discovery.users.list?limit=5",
      ]
      external_token_todo : templatefile("${path.module}/docs/slack/discovery-api/instructions.tftpl", {
        path_to_instance_parameters = "PSOXY_SLACK_DISCOVERY_API_"
      })
      instructions_template = "${path.module}/docs/slack/discovery-api/instructions.tftpl"
    }
    windsurf = {
      source_kind : "windsurf"
      availability : "alpha",
      enable_by_default : false
      worklytics_connector_id : "windsurf-psoxy"
      display_name : "Windsurf",
      worklytics_connector_name : "Windsurf via Psoxy"
      target_host : "server.codeium.com"
      source_auth_strategy : "windsurf_service_key"
      secured_variables : [
        {
          name : "SERVICE_KEY"
          writable : false
          sensitive : true
          value_managed_by_tf : false
        }
      ]
      example_api_requests : [
        {
          method : "POST"
          path : "/api/v1/UserPageAnalytics"
        }
      ],
      external_token_todo : templatefile("${path.module}/docs/windsurf/instructions.tftpl", {
        path_to_instance_parameters = "PSOXY_WINDSURF_"
      })
      instructions_template = "${path.module}/docs/windsurf/instructions.tftpl"
    }
    zoom = {
      source_kind : "zoom"
      availability : "ga",
      enable_by_default : false
      worklytics_connector_id : "zoom-psoxy"
      display_name : "Zoom"
      worklytics_connector_name : "Zoom via Psoxy"
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
          sensitive : false # zoom renders in clear in console
          value_managed_by_tf : false
          description : "Client ID of the Zoom 'Server-to-Server' OAuth App used by the Connector to retrieve Zoom data. Value should be obtained from your Zoom admin."
        },
        {
          name : "ACCOUNT_ID"
          writable : false
          sensitive : false # zoom renders in clear in console
          value_managed_by_tf : false
          description : "Account ID of the Zoom tenant from which the Connector will retrieve Zoom data. Value should be obtained from your Zoom admin."
        },
        {
          name : "ACCESS_TOKEN"
          writable : true # access token
          sensitive : true
          value_managed_by_tf : false
          description : "Short-lived oauth access_token. Filled by Proxy instance."
        },
        local.standard_config_values.oauth_refresh_token_lock,
      ],
      reserved_concurrent_executions : null # 1
      enable_side_output : false
      example_api_calls_user_to_impersonate : null
      example_api_calls : [
        "/v2/users",
        "/v2/users/{USER_ID}/meetings",
        "/v2/users/{USER_ID}/settings",
        "/v2/users/{USER_ID}/recordings",
        "/v2/meetings/{MEETING_ID}",
        "/v2/meetings/{MEETING_ID}/meeting_summary",
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
    * `meeting:read:summary:admin`
    * `cloud_recording:read:list_user_recordings:admin`
    * `report:read:list_meeting_participants:admin`
    * `report:read:meeting:admin`
    * `report:read:user:admin`
    * `user:read:user:admin`
    * `user:read:list_users:admin`
    * `user:read:settings:admin`

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
        {
          name : "ACCESS_TOKEN"
          writable : true # access token
          sensitive : true
          value_managed_by_tf : false
          description : "Short-lived oauth access_token. Filled by Proxy instance."
        },
      ],
      environment_variables : {
        GRANT_TYPE : "refresh_token"
        REFRESH_ENDPOINT : "https://api.dropboxapi.com/oauth2/token"
      }
      reserved_concurrent_executions : null
      enable_side_output : false
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
    confluence-cloud = {
      source_kind : "confluence"
      availability : "beta"
      enable_by_default : false
      worklytics_connector_id : "confluence-cloud-psoxy"
      target_host : "api.atlassian.com"
      source_auth_strategy : "oauth2_refresh_token"
      display_name : "Confluence Cloud"
      worklytics_connector_name : "Confluence Cloud via Psoxy"
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
        local.standard_config_values.oauth_refresh_token_lock,
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
      enable_side_output : false
      example_api_calls_user_to_impersonate : null
      example_api_calls : [
        "/oauth/token/accessible-resources", # obtain Confluence Cloud ID from here
        "/ex/confluence/${local.confluence_example_cloud_id}/wiki/rest/api/group",
        "/ex/confluence/${local.confluence_example_cloud_id}/wiki/rest/api/content/search?cql=lastmodified>=${formatdate("YYYY-MM-DD", timeadd(var.example_api_calls_sample_date, "-720h"))}%20AND%20lastmodified<=${formatdate("YYYY-MM-DD", var.example_api_calls_sample_date)}&limit=30&expand=body.atlas_doc_format,ancestors,version,history,history.previousVersion&includeArchivedSpaces=true",
        "/ex/confluence/${local.confluence_example_cloud_id}/wiki/api/v2/spaces",
        "/ex/confluence/${local.confluence_example_cloud_id}/wiki/api/v2/attachments/{attachmentId}/versions",
        "/ex/confluence/${local.confluence_example_cloud_id}/wiki/api/v2/blogposts/{blogpostId}/versions",
        "/ex/confluence/${local.confluence_example_cloud_id}/wiki/api/v2/pages/{pageId}/versions",
        "/ex/confluence/${local.confluence_example_cloud_id}/wiki/api/v2/footer-comments/{commentId}/versions",
        "/ex/confluence/${local.confluence_example_cloud_id}/wiki/api/v2/inline-comments/{commentId}/versions",
        "/ex/confluence/${local.confluence_example_cloud_id}/wiki/api/v2/tasks",
      ],
      external_token_todo : <<EOT
## Prerequisites
Confluence OAuth 2.0 (3LO) through Psoxy requires a Confluence Cloud account with following granular scopes:

Add following scopes as part of \"Granular Scopes\", first clicking on \`Edit Scopes\` and then selecting them:
    - read:blogpost:confluence: for getting blogposts and their versions
    - read:comment:confluence: for getting comments and their versions
    - read:group:confluence: for getting groups
    - read:space:confluence: for getting spaces
    - read:attachment:confluence: for getting attachments and their versions
    - read:page:confluence: for getting pages and their versions
    - read:user:confluence: for getting users
    - read:task:confluence: for getting tasks
    - read:content-details:confluence: for using content search endpoint
    - read:content:confluence: for using content search endpoint

  Then go back to \"Permissions\" and click on \"Add\" for \`User Identity API\`, only selecting following scopes:
    - read:account: for getting user emails
## Setup Instructions

### App configuration
1. Go to the [Atlassian Developer Console](https://developer.atlassian.com/console/myapps/) and
   click on "Create" (OAuth 2.0 integration).
2. Now navigate to "Permissions" and click on "Add" for Confluence. Once added, click on "Configure".
   Add following scopes as part of \"Granular Scopes\", first clicking on \`Edit Scopes\` and then selecting them:
    - `read:blogpost:confluence`
    - `read:comment:confluence`
    - `read:group:confluence`
    - `read:space:confluence`
    - `read:attachment:confluence`
    - `read:page:confluence`
    - `read:user:confluence`
    - `read:task:confluence`
    - `read:content-details:confluence`
    - `read:content:confluence`
   Then repeat the same but for "User Identity API", adding the following scope:
   - `read:account`
3. Go to the "Authorization" section and add an OAuth 2.0 (3LO) authorization type: click on "Add"
   and you will be prompted to provide a "Callback URI". At this point, you could add
   `http://localhost` as value and follow the [Manual steps](#manual-steps), or you could
   use our [Psoxy OAuth setup tool](#worklytics-psoxy-oauth-setup-tool) (see details below).

### Worklytics OAuth setup tool
Assuming you've created a Confluence Cloud OAuth 2.0 (3LO) integration as described above, from the
use our [Psoxy OAuth setup tool](https://github.com/Worklytics/psoxy-oauth-setup-tool) to obtain
the necessary OAuth tokens and your Confluence Cloud ID.
Once you've installed and run the tool, you will get a Callback URI like this:
`http://localhost:9000/psoxy-setup-callback` (instead of just `http://localhost`) that you can
use in the "Authorization" section of the Developer Console. The tool is interactive, and you
will be prompted to confirm that you've registered the Callback URI before continuing.
Then, you will be prompted to enter the "Client ID" and "Secret" from the Developer Console, and
the tool will open a web browser to perform the authentication and authorization flows. After that,
it will print the all the values to complete the configuration:
- OAuth tokens, Client ID and Secret to be stored in AWS System Manager parameters store / GCP
  Cloud Secrets (if default implementation).

### Manual steps
1. Assuming you've created a Confluence Cloud OAuth 2.0 (3LO) integration as described above, go to
   "Settings" and copy the "Client ID" and "Secret". You will use these to obtain an OAuth
   `refresh_token`.
2. Build an OAuth authorization endpoint URL by copying the value for "Client Id" obtained in the
   previous step into the URL below. Then open the result in a web browser:
   `https://auth.atlassian.com/authorize?audience=api.atlassian.com&client_id=<CLIENT ID>&scope=offline_access%20read%3Ablogpost%3Aconfluence%20read%3Acomment%3Aconfluence%20read%3Agroup%3Aconfluence%20read%3Aspace%3Aconfluence%20read%3Aattachment%3Aconfluence%20read%3Apage%3Aconfluence%20read%3Auser%3Aconfluence%20read%3Atask%3Aconfluence%20read%3Acontent-details%3Aconfluence%20read%3Acontent%3Aconfluence&redirect_uri=http%3A%2F%2Flocalhost&state=YOUR_USER_BOUND_VALUE&response_type=code&prompt=consent`
3. Choose a site in your Confluence workspace to allow access for this application and click "Accept".
   As the callback does not exist, you will see an error. But in the URL of your browser you will
   see something like this as URL:
   `http://localhost/?state=YOUR_USER_BOUND_VALUE&code=eyJhbGc...`
   Copy the value of the `code` parameter from that URI. It is the "authorization code" required
   for next step.
   **NOTE** This "Authorization Code" is single-use; if it expires or is used, you will need to
   obtain a new code by  again pasting the authorization URL in the browser.
4. Now, replace the values in following URL and run it from command line in your terminal. Replace
   `YOUR_AUTHENTICATION_CODE`, `YOUR_CLIENT_ID` and `YOUR_CLIENT_SECRET` in the placeholders:
   `curl --request POST --url 'https://auth.atlassian.com/oauth/token' --header 'Content-Type: application/json' --data '{"grant_type": "authorization_code","client_id": "YOUR_CLIENT_ID","client_secret": "YOUR_CLIENT_SECRET", "code": "YOUR_AUTHENTICATION_CODE", "redirect_uri": "http://localhost"}'`
5. After running that command, if successful you will see a
   [JSON response](https://developer.atlassian.com/cloud/confluence/platform/oauth-2-3lo-apps/#2--exchange-authorization-code-for-access-token) like this:
   ```json
   {
    "access_token": "some short live access token",
    "expires_in": 3600,
    "token_type": "Bearer",
    "refresh_token": "some long live token we are going to use",
    "scopes": [
            "read:attachment:confluence",
            "read:blogpost:confluence",
            "read:comment:confluence",
            "read:content-details:confluence",
            "read:content:confluence",
            "read:group:confluence",
            "read:page:confluence",
            "read:space:confluence",
            "read:user:confluence"
        ],
   }
   ```
6. Set the following variables in AWS System Manager parameters store / GCP Cloud Secrets (if default implementation):
   - `PSOXY_CONFLUENCE_CLOUD_ACCESS_TOKEN` secret variable with value of `access_token` received in previous response
   - `PSOXY_CONFLUENCE_CLOUD_REFRESH_TOKEN` secret variable with value of `refresh_token` received in previous response
   - `PSOXY_CONFLUENCE_CLOUD_CLIENT_ID` with `Client Id` value.
   - `PSOXY_CONFLUENCE_CLOUD_CLIENT_SECRET` with `Client Secret` value.
7. Optional, obtain the "Cloud ID" of your Jira instance. Use the following command, with the
   `access_token` obtained in the previous step in place of `<ACCESS_TOKEN>` below:
   `curl --header 'Authorization: Bearer <ACCESS_TOKEN>' --url 'https://api.atlassian.com/oauth/token/accessible-resources'`
   And its response will be something like:
   ```json
   [
     {
       "id":"SOME UUID",
       "url":"https://your-site.atlassian.net",
       "name":"your-site-name",
        "scopes": [
            "read:attachment:confluence",
            "read:blogpost:confluence",
            "read:comment:confluence",
            "read:content-details:confluence",
            "read:content:confluence",
            "read:group:confluence",
            "read:page:confluence",
            "read:space:confluence",
            "read:user:confluence"
        ],
       "avatarUrl":"https://site-admin-avatar-cdn.prod.public.atl-paas.net/avatars/240/rocket.png"
     }
   ]
   ```
Add the `id` value from that JSON response as the value of the `confluence_cloud_id` variable in the
`terraform.tfvars` file of your Terraform configuration. This will generate all the test URLs with
a proper value.

EOT
    }
    jira-server = {
      source_kind : "jira-server"
      availability : "ga",
      enable_by_default : false
      worklytics_connector_id : "jira-server-psoxy"
      target_host : var.jira_server_url
      source_auth_strategy : "oauth2_access_token"
      display_name : "Jira Data Center"
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
      enable_side_output : false
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
      enable_by_default : false
      worklytics_connector_id : "jira-cloud-psoxy"
      target_host : "api.atlassian.com"
      source_auth_strategy : "oauth2_refresh_token"
      display_name : "Jira REST API"
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
        local.standard_config_values.oauth_refresh_token_lock,
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
      enable_side_output : false
      example_api_calls_user_to_impersonate : null
      example_api_calls : [
        "/oauth/token/accessible-resources", # obtain Atlassian Cloud ID from here
        "/ex/jira/${local.jira_example_cloud_id}/rest/api/3/users",
        "/ex/jira/${local.jira_example_cloud_id}/rest/api/3/group/bulk",
        "/ex/jira/${local.jira_example_cloud_id}/rest/api/3/issue/${local.jira_example_issue_id}/changelog?maxResults=25",
        "/ex/jira/${local.jira_example_cloud_id}/rest/api/3/issue/${local.jira_example_issue_id}/comment?maxResults=25",
        "/ex/jira/${local.jira_example_cloud_id}/rest/api/3/issue/${local.jira_example_issue_id}/worklog?maxResults=25",
        "/ex/jira/${local.jira_example_cloud_id}/rest/api/3/project/search?maxResults=25",
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

### App configuration
1. Go to the [Atlassian Developer Console](https://developer.atlassian.com/console/myapps/) and
   click on "Create" (OAuth 2.0 integration).
2. Now navigate to "Permissions" and click on "Add" for Jira. Once added, click on "Configure".
   Add the following scopes as part of "Classic Scopes":
   - `read:jira-user`
   - `read:jira-work`
   And these from "Granular Scopes":
   - `read:group:jira`
   - `read:avatar:jira`
   - `read:user:jira`
   Then repeat the same but for "User Identity API", adding the following scope:
   - `read:account`
3. Go to the "Authorization" section and add an OAuth 2.0 (3LO) authorization type: click on "Add"
   and you will be prompted to provide a "Callback URI". At this point, you could add
   `http://localhost` as value and follow the [Manual steps](#manual-steps), or you could
   use our [Psoxy OAuth setup tool](#worklytics-psoxy-oauth-setup-tool) (see details below).

### Worklytics OAuth setup tool
Assuming you've created a Jira Cloud OAuth 2.0 (3LO) integration as described above, from the
use our [Psoxy OAuth setup tool](https://github.com/Worklytics/psoxy-oauth-setup-tool) to obtain
the necessary OAuth tokens and your Jira Cloud ID.
Once you've installed and run the tool, you will get a Callback URI like this:
`http://localhost:9000/psoxy-setup-callback` (instead of just `http://localhost`) that you can
use in the "Authorization" section of the Developer Console. The tool is interactive, and you
will be prompted to confirm that you've registered the Callback URI before continuing.
Then, you will be prompted to enter the "Client ID" and "Secret" from the Developer Console, and
the tool will open a web browser to perform the authentication and authorization flows. After that,
it will print the all the values to complete the configuration:
- OAuth tokens, Client ID and Secret to be stored in AWS System Manager parameters store / GCP
  Cloud Secrets (if default implementation).
- Jira Cloud ID as value of the `jira_cloud_id` variable in the `terraform.tfvars` file of your
  Terraform configuration.

### Manual steps
1. Assuming you've created a Jira Cloud OAuth 2.0 (3LO) integration as described above, go to
   "Settings" and copy the "Client ID" and "Secret". You will use these to obtain an OAuth
   `refresh_token`.
2. Build an OAuth authorization endpoint URL by copying the value for "Client Id" obtained in the
   previous step into the URL below. Then open the result in a web browser:
   `https://auth.atlassian.com/authorize?audience=api.atlassian.com&client_id=<CLIENT ID>&scope=offline_access%20read:group:jira%20read:avatar:jira%20read:user:jira%20read:account%20read:jira-user%20read:jira-work&redirect_uri=http://localhost&state=YOUR_USER_BOUND_VALUE&response_type=code&prompt=consent`
3. Choose a site in your Jira workspace to allow access for this application and click "Accept".
   As the callback does not exist, you will see an error. But in the URL of your browser you will
   see something like this as URL:
   `http://localhost/?state=YOUR_USER_BOUND_VALUE&code=eyJhbGc...`
   Copy the value of the `code` parameter from that URI. It is the "authorization code" required
   for next step.
   **NOTE** This "Authorization Code" is single-use; if it expires or is used, you will need to
   obtain a new code by  again pasting the authorization URL in the browser.
4. Now, replace the values in following URL and run it from command line in your terminal. Replace
   `YOUR_AUTHENTICATION_CODE`, `YOUR_CLIENT_ID` and `YOUR_CLIENT_SECRET` in the placeholders:
   `curl --request POST --url 'https://auth.atlassian.com/oauth/token' --header 'Content-Type: application/json' --data '{"grant_type": "authorization_code","client_id": "YOUR_CLIENT_ID","client_secret": "YOUR_CLIENT_SECRET", "code": "YOUR_AUTHENTICATION_CODE", "redirect_uri": "http://localhost"}'`
5. After running that command, if successful you will see a
   [JSON response](https://developer.atlassian.com/cloud/jira/platform/oauth-2-3lo-apps/#2--exchange-authorization-code-for-access-token) like this:
   ```json
   {
     "access_token": "some short live access token",
     "expires_in": 3600,
     "token_type": "Bearer",
     "refresh_token": "some long live token we are going to use",
     "scope": "read:jira-work offline_access read:jira-user"
   }
   ```
6. Set the following variables in AWS System Manager parameters store / GCP Cloud Secrets (if default implementation):
   - `PSOXY_JIRA_CLOUD_ACCESS_TOKEN` secret variable with value of `access_token` received in previous response
   - `PSOXY_JIRA_CLOUD_REFRESH_TOKEN` secret variable with value of `refresh_token` received in previous response
   - `PSOXY_JIRA_CLOUD_CLIENT_ID` with `Client Id` value.
   - `PSOXY_JIRA_CLOUD_CLIENT_SECRET` with `Client Secret` value.
7. Optional, obtain the "Cloud ID" of your Jira instance. Use the following command, with the
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
      availability              = "ga"
      enable_by_default         = false
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
    "gemini-usage" = {
      source_kind               = "gemini"
      availability              = "alpha"
      enable_by_default         = false
      worklytics_connector_id   = "bulk-import-psoxy",
      worklytics_connector_name = "Bulk Data Import via Psoxy"
      rules = {
        columnsToRedact = []
        columnsToPseudonymize = [
          "Email"
        ]
      }
      settings_to_provide = {
        "Parser" = "gemini-usage"
      }
      example_file          = "docs/sources/google-workspace/gemini-usage/example.csv"
      instructions_template = "${path.module}/docs/gemini-usage/instructions.tftpl"
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
          "EMPLOYEE_EMAIL", # for linking to other data sources
          "MANAGER_ID",     # should match to employee_id
        ]
        columnsToPseudonymizeIfPresent = [
          "MANAGER_EMAIL"
        ]
      }
      settings_to_provide = {
        "Parser" = "EMPLOYEE_SNAPSHOT"
      }
      example_file = "docs/sources/hris/hris-example.csv"
    }
    "metrics" = {
      source_kind               = "metrics"
      availability              = "beta"
      enable_by_default         = false
      worklytics_connector_id   = "metrics-import-psoxy",
      worklytics_connector_name = "Metrics via Psoxy"
      rules = {
        columnsToPseudonymizeIfPresent = [
          "EMPLOYEE_ID",
          "EMPLOYEE_EMAIL",
        ]
      }
      settings_to_provide = {
      }
      example_file = "docs/sources/metrics/metrics-example.csv"
    }
    "survey" = {
      worklytics_connector_id   = "survey-import-psoxy"
      availability              = "ga"
      enable_by_default         = false
      source_kind               = "survey"
      worklytics_connector_name = "Survey Data Import via Psoxy"
      rules = {
        columnsToRedact = []
        columnsToPseudonymize = [
          "EMPLOYEE_ID", # primary key; transform FAILS if not present
        ]
        columnsToPseudonymizeIfPresent = [
          "EMPLOYEE_EMAIL" # just in case sent
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
        ]
        columnsToPseudonymizeIfPresent = [
          "EMPLOYEE_EMAIL" # just in case sent
        ]
      }
      example_file = "docs/sources/survey/survey-example.csv"
    }
  }

  oauth_long_access_connectors_backwards = { for k, v in local.oauth_long_access_connectors :
  k => merge(v, { example_calls : try(v.example_api_calls, []) }) }


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

  # backwards-compatible for v0.4.x; remove in v0.5.x
  google_workspace_sources_backwards = { for k, v in local.google_workspace_sources :
  k => merge(v, { example_calls : try(v.example_api_calls, []) }) }

  # backwards-compatible for v0.4.x; remove in v0.5.x
  msft_365_connectors_backwards = { for k, v in local.msft_365_connectors :
  k => merge(v, { example_calls : try(v.example_api_calls, []) }) }

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
