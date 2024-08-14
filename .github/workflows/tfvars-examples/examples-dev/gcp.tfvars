# terraform.tfvars
# this file sets the values of variables for your Terraform configuration. You should manage it under
# version control. anyone working with the infrastructure created by this Terraform configuration will need it
# -- initialized with v0.4.40 of tools/init-tfvars.sh --

# GCP project in which required infrastructure will be provisioned
gcp_project_id = "demo-demo"

# GCP service account emails in the list below will be allowed to invoke your proxy instances
#  - NOTE: this value only applies to GCP deployments
#  - for initial testing/deployment, it can be empty list; it needs to be filled only once you're ready to authorize Worklytics to access your data
worklytics_sa_emails = [
]

# GCP project in which OAuth clients for Google Workspace connectors will be provisioned
#  - if you're not connecting to Google Workspace data sources, you can omit this value
google_workspace_gcp_project_id = "demo-demo"

# Google Workspace example user
#  - this is used to aid testing of Google Workspace connectors against a real account (eg, your own); if you're not using those, it can be omitted
google_workspace_example_user = "demo@acme.com"

# Google Workspace example admin
#  - this is used to aid testing of Google Workspace connectors against a real account, in cases where an admin is explicitly required
google_workspace_example_admin = "demo@acme.com"

# Azure AD Apps (Microsoft API Clients) will be provisioned in the following tenant to access your Microsoft 365 data
#  - this should be the ID of your Microsoft 365 organization (tenant)
#  - if you're not connecting to Microsoft 365 data sources, you can omit this value
msft_tenant_id = "abcdabcd-abcd-abcd-abcd-ab0123456789"

# review following list of connectors to enable, and comment out what you don't want
enabled_connectors = [
  "asana",
  "azure-ad",
  #  "badge",
  #  "dropbox-business",
  "gcal",
  "gdirectory",
  "gdrive",
  #  "github",
  #  "gmail",
  "google-chat",
  #  "google-meet",
  "hris",
  #  "jira-cloud",
  #  "jira-server",
  "outlook-cal",
  "outlook-mail",
  "msft-teams",
  #  "qualtrics",
  #  "salesforce",
  #  "slack-discovery-api",
  #  "survey",
  "zoom",
]

custom_bulk_connectors = {
}
