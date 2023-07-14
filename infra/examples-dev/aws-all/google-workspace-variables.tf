variable "google_workspace_gcp_project_id" {
  type        = string
  description = "string ID of GCP project that will host oauth clients for Google Workspace API connectors; must exist"
  default = null
}

variable "google_workspace_terraform_sa_account_email" {
  type        = string
  description = "Email of GCP service account that will be used to provision GCP resources. Leave 'null' to use application default for you environment."
  default     = null

  validation {
    condition     = var.google_workspace_terraform_sa_account_email == null || can(regex(".*@.*\\.iam\\.gserviceaccount\\.com$", var.google_workspace_terraform_sa_account_email))
    error_message = "The gcp_terraform_sa_account_email value should be a valid GCP service account email address."
  }
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

locals {
  # tflint-ignore: terraform_unused_declarations
  validate_google_workspace_gcp_project_id         = (var.google_workspace_gcp_project_id == null || var.google_workspace_gcp_project_id == "") && (contains(var.enabled_connectors, "gdirectory") || contains(var.enabled_connectors, "gcal") || contains(var.enabled_connectors, "gmail") || contains(var.enabled_connectors, "google-chat") || contains(var.enabled_connectors, "google-meet") || contains(var.enabled_connectors, "gdrive"))
  validate_google_workspace_gcp_project_id_message = "The google_workspace_gcp_project_id var should be populated if a Google Workspace connector is enabled."
  validate_google_workspace_gcp_project_id_check = regex(
    "^${local.validate_google_workspace_gcp_project_id_message}$",
    (!local.validate_google_workspace_gcp_project_id
    ? local.validate_google_workspace_gcp_project_id_message
    : ""))
}