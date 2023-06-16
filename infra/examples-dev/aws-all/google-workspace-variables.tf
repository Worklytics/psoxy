variable "gcp_project_id" {
  type        = string
  description = "string ID of GCP project that will host psoxy instance; must exist. Can leave null if not using GCP/Google Workspace."
  default     = null
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
