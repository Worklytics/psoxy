# Asana
moved {
  from = module.api_connector["asana"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["ACCESS_TOKEN"]
  to   = google_secret_manager_secret_iam_member.grant_sa_secretAccessor_on_non_tf_secret["ASANA_ACCESS_TOKEN"]
}


# Dropbox - no known deployments

# Github

moved {
  from = module.api_connector["github"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["CLIENT_ID"]
  to   = google_secret_manager_secret_iam_member.grant_sa_secretAccessor_on_non_tf_secret["GITHUB_CLIENT_ID"]
}

moved {
  from = module.api_connector["github"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["PRIVATE_KEY"]
  to   = google_secret_manager_secret_iam_member.grant_sa_secretAccessor_on_non_tf_secret["GITHUB_PRIVATE_KEY"]
}

# Jira Cloud
moved {
  from = module.api_connector["jira-cloud"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["CLIENT_ID"]
  to   = google_secret_manager_secret_iam_member.grant_sa_secretAccessor_on_non_tf_secret["JIRA_CLOUD_CLIENT_ID"]
}

moved {
  from = module.api_connector["jira-cloud"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["CLIENT_SECRET"]
  to   = google_secret_manager_secret_iam_member.grant_sa_secretAccessor_on_non_tf_secret["JIRA_CLOUD_CLIENT_SECRET"]
}

# Jira Server
moved {
  from = module.api_connector["jira-server"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["ACCESS_TOKEN"]
  to   = google_secret_manager_secret_iam_member.grant_sa_secretAccessor_on_non_tf_secret["JIRA_SERVER_ACCESS_TOKEN"]
}

# Salesforce
moved {
  from = module.api_connector["salesforce"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["CLIENT_ID"]
  to   = google_secret_manager_secret_iam_member.grant_sa_secretAccessor_on_non_tf_secret["SALESFORCE_CLIENT_ID"]
}

moved {
  from = module.api_connector["salesforce"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["CLIENT_SECRET"]
  to   = google_secret_manager_secret_iam_member.grant_sa_secretAccessor_on_non_tf_secret["SALESFORCE_CLIENT_SECRET"]
}



# Slack

moved {
  from = module.api_connector["slack-discovery-api"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["ACCESS_TOKEN"]
  to   = google_secret_manager_secret_iam_member.grant_sa_secretAccessor_on_non_tf_secret["SLACK_DISCOVERY_API_ACCESS_TOKEN"]
}

# Zoom
moved {
  from = module.api_connector["zoom"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["CLIENT_ID"]
  to   = google_secret_manager_secret_iam_member.grant_sa_secretAccessor_on_non_tf_secret["ZOOM_CLIENT_ID"]
}

moved {
  from = module.api_connector["zoom"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["CLIENT_SECRET"]
  to   = google_secret_manager_secret_iam_member.grant_sa_secretAccessor_on_non_tf_secret["ZOOM_CLIENT_SECRET"]
}

moved {
  from = module.api_connector["zoom"].google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret["ACCOUNT_ID"]
  to   = google_secret_manager_secret_iam_member.grant_sa_secretAccessor_on_non_tf_secret["ZOOM_ACCOUNT_ID"]
}


