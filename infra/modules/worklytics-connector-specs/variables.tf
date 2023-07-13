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
  description = "(Only required if using Jira Server connector) URL of the Jira server (ex: myjiraserver.mycompany.com)"
}

variable "jira_cloud_id" {
  type        = string
  default     = null
  description = "(Only required if using Jira Cloud connector) Cloud id of the Jira Cloud to connect to (ex: 1324a887-45db-1bf4-1e99-ef0ff456d421)."
}

variable "example_jira_issue_id" {
  type        = string
  default     = null
  description = "(Only required if using Jira Server/Cloud connector) Id of an issue for only to be used as part of example calls for Jira (ex: ETV-12)"
}

variable "github_installation_id" {
  type        = string
  default     = null
  description = "(Only required if using Github connector) AppId of the application used for authentication with the proxy instance (ex: 123456)"
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