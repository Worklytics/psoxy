
# 0.4.26 --> 0.4.27
# refactored secret provisioning, to simplify binding secrets to cloud function env variables
# this is no-op on underlying secret infra, but changes keys in Terraform resources
# moves here enumerate all possible connectors, to move any secrets that they have
# moves that refer to things that don't exist are no-ops, including stuff going *to* stuff that
# doesn't exist
#

# Google Workspace

moved {
  from = module.secrets["gdirectory"].google_secret_manager_secret.secret["GDIRECTORY_SERVICE_ACCOUNT_KEY"]
  to   = module.secrets["gdirectory"].google_secret_manager_secret.secret["SERVICE_ACCOUNT_KEY"]
}
moved {
  from = module.secrets["gdirectory"].google_secret_manager_secret_version.version["GDIRECTORY_SERVICE_ACCOUNT_KEY"]
  to   = module.secrets["gdirectory"].google_secret_manager_secret_version.version["SERVICE_ACCOUNT_KEY"]
}
moved {
  from = module.api_connector["gdirectory"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["GDIRECTORY_SERVICE_ACCOUNT_KEY"]
  to   = module.api_connector["gdirectory"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["SERVICE_ACCOUNT_KEY"]
}

moved {
  from = module.secrets["gcal"].google_secret_manager_secret.secret["GCAL_SERVICE_ACCOUNT_KEY"]
  to   = module.secrets["gcal"].google_secret_manager_secret.secret["SERVICE_ACCOUNT_KEY"]
}
moved {
  from = module.secrets["gcal"].google_secret_manager_secret_version.version["GCAL_SERVICE_ACCOUNT_KEY"]
  to   = module.secrets["gcal"].google_secret_manager_secret_version.version["SERVICE_ACCOUNT_KEY"]
}
moved {
  from = module.api_connector["gcal"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["GCAL_SERVICE_ACCOUNT_KEY"]
  to   = module.api_connector["gcal"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["SERVICE_ACCOUNT_KEY"]
}

moved {
  from = module.secrets["gmail"].google_secret_manager_secret.secret["GMAIL_SERVICE_ACCOUNT_KEY"]
  to   = module.secrets["gmail"].google_secret_manager_secret.secret["SERVICE_ACCOUNT_KEY"]
}
moved {
  from = module.secrets["gmail"].google_secret_manager_secret_version.version["GMAIL_SERVICE_ACCOUNT_KEY"]
  to   = module.secrets["gmail"].google_secret_manager_secret_version.version["SERVICE_ACCOUNT_KEY"]
}
moved {
  from = module.api_connector["gmail"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["GMAIL_SERVICE_ACCOUNT_KEY"]
  to   = module.api_connector["gmail"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["SERVICE_ACCOUNT_KEY"]
}

moved {
  from = module.secrets["google-chat"].google_secret_manager_secret.secret["GOOGLE_CHAT_SERVICE_ACCOUNT_KEY"]
  to   = module.secrets["google-chat"].google_secret_manager_secret.secret["SERVICE_ACCOUNT_KEY"]
}
moved {
  from = module.secrets["google-chat"].google_secret_manager_secret_version.version["GOOGLE_CHAT_SERVICE_ACCOUNT_KEY"]
  to   = module.secrets["google-chat"].google_secret_manager_secret_version.version["SERVICE_ACCOUNT_KEY"]
}
moved {
  from = module.api_connector["google-chat"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["GOOGLE_CHAT_SERVICE_ACCOUNT_KEY"]
  to   = module.api_connector["google-chat"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["SERVICE_ACCOUNT_KEY"]
}

moved {
  from = module.secrets["google-meet"].google_secret_manager_secret.secret["GOOGLE_MEET_SERVICE_ACCOUNT_KEY"]
  to   = module.secrets["google-meet"].google_secret_manager_secret.secret["SERVICE_ACCOUNT_KEY"]
}
moved {
  from = module.secrets["google-meet"].google_secret_manager_secret_version.version["GOOGLE_MEET_SERVICE_ACCOUNT_KEY"]
  to   = module.secrets["google-meet"].google_secret_manager_secret_version.version["SERVICE_ACCOUNT_KEY"]
}
moved {
  from = module.api_connector["google-meet"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["GOOGLE_MEET_SERVICE_ACCOUNT_KEY"]
  to   = module.api_connector["google-meet"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["SERVICE_ACCOUNT_KEY"]
}

moved {
  from = module.secrets["gdrive"].google_secret_manager_secret.secret["GDRIVE_SERVICE_ACCOUNT_KEY"]
  to   = module.secrets["gdrive"].google_secret_manager_secret.secret["SERVICE_ACCOUNT_KEY"]
}
moved {
  from = module.secrets["gdrive"].google_secret_manager_secret_version.version["GDRIVE_SERVICE_ACCOUNT_KEY"]
  to   = module.secrets["gdrive"].google_secret_manager_secret_version.version["SERVICE_ACCOUNT_KEY"]
}
moved {
  from = module.api_connector["gdrive"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["GDRIVE_SERVICE_ACCOUNT_KEY"]
  to   = module.api_connector["gdrive"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["SERVICE_ACCOUNT_KEY"]
}



# OAuth Long Access

# zoom

moved {
  from = module.secrets["zoom"].google_secret_manager_secret.secret["ZOOM_OAUTH_REFRESH_TOKEN"]
  to   = module.secrets["zoom"].google_secret_manager_secret.secret["OAUTH_REFRESH_TOKEN"]
}
moved {
  from = module.secrets["zoom"].google_secret_manager_secret_version.version["ZOOM_OAUTH_REFRESH_TOKEN"]
  to   = module.secrets["zoom"].google_secret_manager_secret_version.version["OAUTH_REFRESH_TOKEN"]
}
moved {
  from = module.api_connector["zoom"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["ZOOM_OAUTH_REFRESH_TOKEN"]
  to   = module.api_connector["zoom"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["OAUTH_REFRESH_TOKEN"]
}

moved {
  from = module.secrets["zoom"].google_secret_manager_secret.secret["ZOOM_ACCESS_TOKEN"]
  to   = module.secrets["zoom"].google_secret_manager_secret.secret["ACCESS_TOKEN"]
}
moved {
  from = module.secrets["zoom"].google_secret_manager_secret_version.version["ZOOM_ACCESS_TOKEN"]
  to   = module.secrets["zoom"].google_secret_manager_secret_version.version["ACCESS_TOKEN"]
}
moved {
  from = module.api_connector["zoom"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["ZOOM_ACCESS_TOKEN"]
  to   = module.api_connector["zoom"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["ACCESS_TOKEN"]
}

moved {
  from = module.secrets["zoom"].google_secret_manager_secret.secret["ZOOM_ACCOUNT_ID"]
  to   = module.secrets["zoom"].google_secret_manager_secret.secret["ACCOUNT_ID"]
}
moved {
  from = module.secrets["zoom"].google_secret_manager_secret_version.version["ZOOM_ACCOUNT_ID"]
  to   = module.secrets["zoom"].google_secret_manager_secret_version.version["ACCOUNT_ID"]
}
moved {
  from = module.api_connector["zoom"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["ZOOM_ACCOUNT_ID"]
  to   = module.api_connector["zoom"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["ACCOUNT_ID"]
}

moved {
  from = module.secrets["zoom"].google_secret_manager_secret.secret["ZOOM_CLIENT_ID"]
  to   = module.secrets["zoom"].google_secret_manager_secret.secret["CLIENT_ID"]
}
moved {
  from = module.secrets["zoom"].google_secret_manager_secret_version.version["ZOOM_CLIENT_ID"]
  to   = module.secrets["zoom"].google_secret_manager_secret_version.version["CLIENT_ID"]
}
moved {
  from = module.api_connector["zoom"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["ZOOM_CLIENT_ID"]
  to   = module.api_connector["zoom"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["CLIENT_ID"]
}

moved {
  from = module.secrets["zoom"].google_secret_manager_secret.secret["ZOOM_CLIENT_SECRET"]
  to   = module.secrets["zoom"].google_secret_manager_secret.secret["CLIENT_SECRET"]
}
moved {
  from = module.secrets["zoom"].google_secret_manager_secret_version.version["ZOOM_CLIENT_SECRET"]
  to   = module.secrets["zoom"].google_secret_manager_secret_version.version["CLIENT_SECRET"]
}
moved {
  from = module.api_connector["zoom"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["ZOOM_CLIENT_SECRET"]
  to   = module.api_connector["zoom"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["CLIENT_SECRET"]
}


# jira-server

moved {
  from = module.secrets["jira-server"].google_secret_manager_secret.secret["JIRA_SERVER_ACCESS_TOKEN"]
  to   = module.secrets["jira-server"].google_secret_manager_secret.secret["ACCESS_TOKEN"]
}
moved {
  from = module.secrets["jira-server"].google_secret_manager_secret_version.version["JIRA_SERVER_ACCESS_TOKEN"]
  to   = module.secrets["jira-server"].google_secret_manager_secret_version.version["ACCESS_TOKEN"]
}
moved {
  from = module.api_connector["jira-server"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["JIRA_SERVER_ACCESS_TOKEN"]
  to   = module.api_connector["jira-server"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["ACCESS_TOKEN"]
}


# jira cloud

moved {
  from = module.secrets["jira-cloud"].google_secret_manager_secret.secret["JIRA_CLOUD_OAUTH_REFRESH_TOKEN"]
  to   = module.secrets["jira-cloud"].google_secret_manager_secret.secret["OAUTH_REFRESH_TOKEN"]
}
moved {
  from = module.secrets["jira-cloud"].google_secret_manager_secret_version.version["JIRA_CLOUD_OAUTH_REFRESH_TOKEN"]
  to   = module.secrets["jira-cloud"].google_secret_manager_secret_version.version["OAUTH_REFRESH_TOKEN"]
}
moved {
  from = module.api_connector["jira-cloud"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["JIRA_CLOUD_ACCESS_TOKEN"]
  to   = module.api_connector["jira-cloud"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["ACCESS_TOKEN"]
}

moved {
  from = module.secrets["jira-cloud"].google_secret_manager_secret.secret["JIRA_CLOUD_ACCESS_TOKEN"]
  to   = module.secrets["jira-cloud"].google_secret_manager_secret.secret["ACCESS_TOKEN"]
}
moved {
  from = module.secrets["jira-cloud"].google_secret_manager_secret_version.version["JIRA_CLOUD_ACCESS_TOKEN"]
  to   = module.secrets["jira-cloud"].google_secret_manager_secret_version.version["ACCESS_TOKEN"]
}
moved {
  from = module.api_connector["jira-cloud"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["JIRA_CLOUD_ACCESS_TOKEN"]
  to   = module.api_connector["jira-cloud"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["ACCESS_TOKEN"]
}

moved {
  from = module.secrets["jira-cloud"].google_secret_manager_secret.secret["JIRA_CLOUD_ACCOUNT_ID"]
  to   = module.secrets["jira-cloud"].google_secret_manager_secret.secret["ACCOUNT_ID"]
}
moved {
  from = module.secrets["jira-cloud"].google_secret_manager_secret_version.version["JIRA_CLOUD_ACCOUNT_ID"]
  to   = module.secrets["jira-cloud"].google_secret_manager_secret_version.version["ACCOUNT_ID"]
}
moved {
  from = module.api_connector["jira-cloud"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["JIRA_CLOUD_ACCOUNT_ID"]
  to   = module.api_connector["jira-cloud"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["ACCOUNT_ID"]
}

moved {
  from = module.secrets["jira-cloud"].google_secret_manager_secret.secret["JIRA_CLOUD_CLIENT_ID"]
  to   = module.secrets["jira-cloud"].google_secret_manager_secret.secret["CLIENT_ID"]
}
moved {
  from = module.secrets["jira-cloud"].google_secret_manager_secret_version.version["JIRA_CLOUD_CLIENT_ID"]
  to   = module.secrets["jira-cloud"].google_secret_manager_secret_version.version["CLIENT_ID"]
}
moved {
  from = module.api_connector["jira-cloud"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["JIRA_CLOUD_CLIENT_ID"]
  to   = module.api_connector["jira-cloud"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["CLIENT_ID"]
}


moved {
  from = module.secrets["jira-cloud"].google_secret_manager_secret.secret["JIRA_CLOUD_CLIENT_SECRET"]
  to   = module.secrets["jira-cloud"].google_secret_manager_secret.secret["CLIENT_SECRET"]
}
moved {
  from = module.secrets["jira-cloud"].google_secret_manager_secret_version.version["JIRA_CLOUD_CLIENT_SECRET"]
  to   = module.secrets["jira-cloud"].google_secret_manager_secret_version.version["CLIENT_SECRET"]
}
moved {
  from = module.api_connector["jira-cloud"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["JIRA_CLOUD_CLIENT_SECRET"]
  to   = module.api_connector["jira-cloud"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["CLIENT_SECRET"]
}

# slack
moved {
  from = module.secrets["slack-discovery-api"].google_secret_manager_secret.secret["SLACK_DISCOVERY_API_ACCESS_TOKEN"]
  to   = module.secrets["slack-discovery-api"].google_secret_manager_secret.secret["ACCESS_TOKEN"]
}
moved {
  from = module.secrets["slack-discovery-api"].google_secret_manager_secret_version.version["SLACK_DISCOVERY_API_ACCESS_TOKEN"]
  to   = module.secrets["slack-discovery-api"].google_secret_manager_secret_version.version["ACCESS_TOKEN"]
}
moved {
  from = module.api_connector["slack-discovery-api"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["SLACK_DISCOVERY_API_ACCESS_TOKEN"]
  to   = module.api_connector["slack-discovery-api"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["ACCESS_TOKEN"]
}


# asana
moved {
  from = module.secrets["asana"].google_secret_manager_secret.secret["ASANA_ACCESS_TOKEN"]
  to   = module.secrets["asana"].google_secret_manager_secret.secret["ACCESS_TOKEN"]
}
moved {
  from = module.secrets["asana"].google_secret_manager_secret_version.version["ASANA_ACCESS_TOKEN"]
  to   = module.secrets["asana"].google_secret_manager_secret_version.version["ACCESS_TOKEN"]
}
moved {
  from = module.api_connector["asana"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["ASANA_ACCESS_TOKEN"]
  to   = module.api_connector["asana"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["ACCESS_TOKEN"]
}


# salesforce

moved {
  from = module.secrets["salesforce"].google_secret_manager_secret.secret["SALESFORCE_CLIENT_ID"]
  to   = module.secrets["salesforce"].google_secret_manager_secret.secret["CLIENT_ID"]
}
moved {
  from = module.secrets["salesforce"].google_secret_manager_secret_version.version["SALESFORCE_CLIENT_ID"]
  to   = module.secrets["salesforce"].google_secret_manager_secret_version.version["CLIENT_ID"]
}
moved {
  from = module.api_connector["salesforce"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["SALESFORCE_CLIENT_ID"]
  to   = module.api_connector["salesforce"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["CLIENT_ID"]
}

moved {
  from = module.secrets["salesforce"].google_secret_manager_secret.secret["SALESFORCE_CLIENT_SECRET"]
  to   = module.secrets["salesforce"].google_secret_manager_secret.secret["CLIENT_SECRET"]
}
moved {
  from = module.secrets["salesforce"].google_secret_manager_secret_version.version["SALESFORCE_CLIENT_SECRET"]
  to   = module.secrets["salesforce"].google_secret_manager_secret_version.version["CLIENT_SECRET"]
}
moved {
  from = module.api_connector["salesforce"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["SALESFORCE_CLIENT_SECRET"]
  to   = module.api_connector["salesforce"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["CLIENT_SECRET"]
}

