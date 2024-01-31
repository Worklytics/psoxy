# moves to migrate some secrets to be explicitly managed externally from Terraform, to avoid reverting
# values/enabled versions

# Asana
moved {
  from = module.instance_secrets["asana"].aws_ssm_parameter.secret["ACCESS_TOKEN"]
  to   = module.instance_secrets["asana"].aws_ssm_parameter.secret_with_externally_managed_value["ACCESS_TOKEN"]
}

# Dropbox and Github - ignored as no one has deployed these yet

# Jira Cloud
moved {
  from = module.instance_secrets["jira-cloud"].aws_ssm_parameter.secret["ACCESS_TOKEN"]
  to   = module.instance_secrets["jira-cloud"].aws_ssm_parameter.secret_with_externally_managed_value["ACCESS_TOKEN"]
}
moved {
  from = module.instance_secrets["jira-cloud"].aws_ssm_parameter.secret["REFRESH_TOKEN"]
  to   = module.instance_secrets["jira-cloud"].aws_ssm_parameter.secret_with_externally_managed_value["REFRESH_TOKEN"]
}
moved {
  from = module.instance_secrets["jira-cloud"].aws_ssm_parameter.secret["OAUTH_REFRESH_TOKEN"]
  to   = module.instance_secrets["jira-cloud"].aws_ssm_parameter.secret_with_externally_managed_value["OAUTH_REFRESH_TOKEN"]
}
moved {
  from = module.instance_secrets["jira-cloud"].aws_ssm_parameter.secret["CLIENT_ID"]
  to   = module.instance_secrets["jira-cloud"].aws_ssm_parameter.secret_with_externally_managed_value["CLIENT_ID"]
}
moved {
  from = module.instance_secrets["jira-cloud"].aws_ssm_parameter.secret["CLIENT_SECRET"]
  to   = module.instance_secrets["jira-cloud"].aws_ssm_parameter.secret_with_externally_managed_value["CLIENT_SECRET"]
}

# Jira Server
moved {
  from = module.instance_secrets["jira-server"].aws_ssm_parameter.secret["ACCESS_TOKEN"]
  to   = module.instance_secrets["jira-server"].aws_ssm_parameter.secret_with_externally_managed_value["ACCESS_TOKEN"]
}

# Salesforce
moved {
  from = module.instance_secrets["salesforce"].aws_ssm_parameter.secret["CLIENT_SECRET"]
  to   = module.instance_secrets["salesforce"].aws_ssm_parameter.secret_with_externally_managed_value["CLIENT_SECRET"]
}
moved {
  from = module.instance_secrets["salesforce"].aws_ssm_parameter.secret["CLIENT_ID"]
  to   = module.instance_secrets["salesforce"].aws_ssm_parameter.secret_with_externally_managed_value["CLIENT_ID"]
}

# Slack Discovery API
moved {
  from = module.instance_secrets["slack-discovery-api"].aws_ssm_parameter.secret["ACCESS_TOKEN"]
  to   = module.instance_secrets["slack-discovery-api"].aws_ssm_parameter.secret_with_externally_managed_value["ACCESS_TOKEN"]
}

# Zoom
moved {
  from = module.instance_secrets["zoom"].aws_ssm_parameter.secret["CLIENT_SECRET"]
  to   = module.instance_secrets["zoom"].aws_ssm_parameter.secret_with_externally_managed_value["CLIENT_SECRET"]
}

moved {
  from = module.instance_secrets["zoom"].aws_ssm_parameter.secret["CLIENT_ID"]
  to   = module.instance_secrets["zoom"].aws_ssm_parameter.secret_with_externally_managed_value["CLIENT_ID"]
}
moved {
  from = module.instance_secrets["zoom"].aws_ssm_parameter.secret["ACCOUNT_ID"]
  to   = module.instance_secrets["zoom"].aws_ssm_parameter.secret_with_externally_managed_value["ACCOUNT_ID"]
}
moved {
  from = module.instance_secrets["zoom"].aws_ssm_parameter.secret["ACCESS_TOKEN"]
  to   = module.instance_secrets["zoom"].aws_ssm_parameter.secret_with_externally_managed_value["ACCESS_TOKEN"]
}
moved {
  from = module.instance_secrets["zoom"].aws_ssm_parameter.secret["OAUTH_REFRESH_TOKEN"]
  to   = module.instance_secrets["zoom"].aws_ssm_parameter.secret_with_externally_managed_value["OAUTH_REFRESH_TOKEN"]
}
