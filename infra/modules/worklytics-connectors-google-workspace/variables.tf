variable "environment_id" {
  type        = string
  description = "Qualifier to append to names/ids of resources. If not empty, A-Za-z0-9 or - characters only. Max length 10. Useful to distinguish between deployments into same GCP project."
  default     = "psoxy"

  validation {
    condition     = can(regex("^[A-z0-9\\-]{0,20}$", var.environment_id))
    error_message = "The environment_name must be 0-20 chars of [A-z0-9\\-] only."
  }
}

variable "enabled_connectors" {
  type        = list(string)
  description = "ids of connectors to enable"
}

variable "config_parameter_prefix" {
  type        = string
  description = "prefix used for configuration parameter names (such as secrets)"
}

variable "gcp_project_id" {
  type        = string
  description = "id of GCP project that will host OAuth Clients for Google Workspace API connectors"
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

variable "todo_step" {
  type        = number
  description = "of all todos, where does this one logically fall in sequence"
  default     = 1
}
