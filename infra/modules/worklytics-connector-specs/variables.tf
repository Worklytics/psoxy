variable "enabled_connectors" {
  type        = list(string)
  description = "ids of connectors to enable"
}

variable "google_workspace_example_user" {
  type        = string
  description = "user to impersonate for Google Workspace API calls (null for none)"
  default     = null
}

variable "google_workspace_example_admin" {
  type        = string
  description = "user to impersonate for Google Workspace API calls (null for value of `google_workspace_example_user`)"
  default     = null # will failover to user
}

variable "msft_tenant_id" {
  type        = string
  description = "ID of Microsoft tenant to connect to (req'd only if config includes MSFT connectors)"
  default     = ""
}

variable "example_msft_user_guid" {
  type        = string
  description = "example MSFT user guid (uuid) for test API calls (OPTIONAL)"
  default     = "{EXAMPLE_MSFT_USER_GUID}"
}

variable "msft_teams_example_team_guid" {
  type        = string
  description = "example of MSFT Id (GUID) of a Teams Team for test API calls (OPTIONAL)"
  default     = "{EXAMPLE_MSFT_TEAMS_TEAM_GUID}"
}

variable "msft_teams_example_channel_guid" {
  type        = string
  description = "example of MSFT Id (GUID) of a Teams Channel for test API calls (OPTIONAL)"
  default     = "{EXAMPLE_MSFT_TEAMS_CHANNEL_GUID}"
}

variable "msft_teams_example_chat_guid" {
  type        = string
  description = "example of MSFT Id (GUID) of a Teams Chat for test API calls (OPTIONAL)"
  default     = "{EXAMPLE_MSFT_TEAMS_CHAT_GUID}"
}

variable "msft_teams_example_call_guid" {
  type        = string
  description = "example of MSFT Id (GUID) of a Teams Call for test API calls (OPTIONAL)"
  default     = "{EXAMPLE_MSFT_TEAMS_CALL_GUID}"
}

variable "msft_teams_example_call_record_guid" {
  type        = string
  description = "example of MSFT Id (GUID) of a Teams Call Record for test API calls (OPTIONAL)"
  default     = "{EXAMPLE_MSFT_TEAMS_CALL_RECORD_GUID}"
}

variable "salesforce_domain" {
  type        = string
  description = "Domain of the Salesforce to connect to (only required if using Salesforce connector). To find your My Domain URL, from Setup, in the Quick Find box, enter My Domain, and then select My Domain"
  default     = ""
}

variable "jira_server_url" {
  type        = string
  default     = null
  description = "(Only required if using Jira Server connector) URL of the Jira server (ex: myjiraserver.mycompany.com)"
}

variable "jira_cloud_id" {
  type        = string
  default     = null
  description = "(Only required if using Jira Cloud connector) Cloud id of the Jira Cloud to connect to (ex: 1324a887-45db-1bf4-1e99-ef0ff456d421)."
}

#DEPRECATED
variable "example_jira_issue_id" {
  type        = string
  default     = null
  description = "Deprecated; use `jira_example_issued_id`. (Only required if using Jira Server/Cloud connector) Id of an issue for only to be used as part of example calls for Jira (ex: ETV-12)"
}

variable "jira_example_issue_id" {
  type        = string
  default     = null
  description = "If using Jira Server/Cloud connector, provide id of an issue for only to be used as part of example calls for Jira (ex: ETV-12)"
}

variable "github_enterprise_server_host" {
  type        = string
  default     = "" # not null, as this is required if using github_enterprise_server. Validation done on the caller
  description = "(Only required if using Github Enterprise Server connector) Host of the Github instance (ex: github.mycompany.com)."
}

variable "github_installation_id" {
  type        = string
  default     = null
  description = "(Only required if using Github connector) InstallationId of the application in your org for authentication with the proxy instance (ex: 123456)"
}

variable "github_organization" {
  type        = string
  default     = null
  description = "(Only required if using Github connector) Name of the organization to be used as part of example calls for Github (ex: Worklytics)"
}

variable "github_example_repository" {
  type        = string
  default     = null
  description = "(Only required if using Github connector) Name for the repository to be used as part of example calls for Github (ex: psoxy)"
}

variable "salesforce_example_account_id" {
  type        = string
  default     = null
  description = "(Only required if using Salesforce connector) Id of the account id for usign as an example calls for Salesforce (ex: 0015Y00002c7g95QAA)"
}