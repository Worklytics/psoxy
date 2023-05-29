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

variable "salesforce_domain" {
  type        = string
  description = "Domain of the Salesforce to connect to (only required if using Salesforce connector). To find your My Domain URL, from Setup, in the Quick Find box, enter My Domain, and then select My Domain"
  default     = ""
}

variable "jira_server_url" {
  type        = string
  default     = null
  description = "URL of the Jira server (only required if using Jira Server connector)."
}

variable "jira_cloud_id" {
  type        = string
  default     = null
  description = "Cloud id of the Jira Cloud to connect to (only required if using Jira Cloud connector)."
}

variable "example_jira_issue_id" {
  type        = string
  default     = null
  description = "Id of an issue for only to be used as part of example calls for Jira (only required if using Jira Server/Cloud connector)."
}