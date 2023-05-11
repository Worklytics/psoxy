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
  default     = null
  description = "ID of Microsoft tenant to connect to (req'd only if config includes MSFT connectors)"
  validation {
    condition     = var.msft_tenant_id != null || length([for c in var.enabled_connectors : c if contains(["azure-ad", "outlook-cal", "outlook-mail"], c)]) == 0
    error_message = "msft_tenant_id is required if any MSFT connectors are enabled"
  }
}

variable "example_msft_user_guid" {
  type        = string
  description = "example MSFT user guid (uuid) for test API calls (OPTIONAL)"
  default     = "{EXAMPLE_MSFT_USER_GUID}"
}

variable "salesforce_domain" {
  type        = string
  default     = ""
  description = "Domain of the Salesforce to connect to (only required if using Salesforce connector). To find your My Domain URL, from Setup, in the Quick Find box, enter My Domain, and then select My Domain"
}
