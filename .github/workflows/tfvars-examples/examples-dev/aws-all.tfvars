# terraform.tfvars
# this file sets the values of variables for your Terraform configuration. You should manage it under
# version control. anyone working with the infrastructure created by this Terraform configuration will need it
# -- initialized with v0.4.40 of tools/init-tfvars.sh --

# root directory of a clone of the psoxy repo
#  - by default, it points to .terraform, where terraform clones the main psoxy repo
#  - if you have a local clone of the psoxy repo you prefer to use, change this to point there
provision_testing_infra = true

# AWS account in which your Psoxy instances will be deployed
aws_account_id = "123123123123"

# AWS region in which your Psoxy infrastructure will be deployed
aws_region = "us-east-1"

# AWS IAM role to assume when deploying your Psoxy infrastructure via Terraform, if needed
# - this variable is used when you are authenticated as an AWS user which can assume the AWS role which actually has the requisite permissions to provision your infrastructure
#   (this is approach is good practice, as minimizes the privileges of the AWS user you habitually use and easily supports multi-account scenarios)
# - if you are already authenticated as a sufficiently privileged AWS Principal, you can omit this variable
# - often, this will be the default 'super-admin' role in the target AWS account, eg something like 'arn:aws:iam::123456789012:role/Admin'
# - see https://github.com/Worklytics/psoxy/blob/v/docs/aws/getting-started.md for details on required permissions
aws_assume_role_arn = "arn:aws:iam::123123123123:role/InfraAdmin" #(double-check this; perhaps needs to be a role within target account)

# AWS principals in the following list will be explicitly authorized to invoke your proxy instances
#  - this is for initial testing/development; it can (and should) be empty for production-use
caller_aws_arns = [
  "arn:aws:iam::123123123123:user/demo" # for testing; can remove once ready for production
]

# GCP service accounts with ids in the list below will be allowed to invoke your proxy instances
#  - for initial testing/deployment, it can be empty list; it needs to be filled only once you're ready to authorize Worklytics to access your data
caller_gcp_service_account_ids = [
  # put 'Service Account Unique ID' value, which you can obtain from Worklytics ( https://intl.worklytics.co/analytics/integrations/configuration )
  # "123456712345671234567" # should be 21-digits
]

# GCP project in which OAuth clients for Google Workspace connectors will be provisioned
#  - if you're not connecting to Google Workspace data sources, you can omit this value
google_workspace_gcp_project_id = "demo-123123"

# Google Workspace example user
#  - this is used to aid testing of Google Workspace connectors against a real account (eg, your own); if you're not using those, it can be omitted
google_workspace_example_user = "demo@acme.org"

# Google Workspace example admin
#  - this is used to aid testing of Google Workspace connectors against a real account, in cases where an admin is explicitly required
google_workspace_example_admin = "admin@acme.org"

# Azure AD Apps (Microsoft API Clients) will be provisioned in the following tenant to access your Microsoft 365 data
#  - this should be the ID of your Microsoft 365 organization (tenant)
#  - if you're not connecting to Microsoft 365 data sources, you can omit this value
msft_tenant_id = "abcdabcd-abcd-abcd-abcd-ab0123456789"

# users in the following list will be set as the 'owners' of the Azure AD Apps (API clients) provisioned to access your Microsoft 365 data
#  - if you're not connecting to Microsoft 365 data sources, you can omit this value
msft_owners_email = [
]

# review following list of connectors to enable, and comment out what you don't want
enabled_connectors = [
  "asana",
  "azure-ad",
  #  "badge",
  #  "dropbox-business",
  "gcal",
  "gdirectory",
  #  "gdrive",
  #  "github",
  #  "gmail",
  "google-chat",
  #  "google-meet",
  "hris",
  "jira-cloud",
  #  "jira-server",
  #  "outlook-cal",
  #  "outlook-mail",
  #  "qualtrics",
  #  "salesforce",
  #  "slack-discovery-api",
  #  "survey",
  "teams",
  "zoom",
]

# environment_name is used to name resources provisioned by this Terraform configuration
force_bundle                    = false
google_workspace_provision_keys = true


todos_as_local_files = false
