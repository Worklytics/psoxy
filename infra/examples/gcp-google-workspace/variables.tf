variable "gcp_project_id" {
  type        = string
  description = "id of GCP project that will host psoxy instance"
}

variable "environment_name" {
  type        = string
  description = "qualifier to append to name of project that will host your psoxy instance"
  default     = null
}

variable "gcp_folder_id" {
  type        = string
  description = "optionally, a folder into which to provision it"
  default     = null
}

variable "gcp_billing_account_id" {
  type        = string
  description = "billing account ID; needed to create the project"
  default     = null
}

variable "gcp_org_id" {
  type        = string
  description = "your GCP organization ID"
  default     = null
}

variable "worklytics_sa_emails" {
  type        = list(string)
  description = "service accounts for your organization's Worklytics instances (list supported for test/dev scenarios)"
}

variable "connector_display_name_suffix" {
  type        = string
  description = "suffix to append to display_names of connector SAs; helpful to distinguish between various ones in testing/dev scenarios"
  default     = ""
}

variable "psoxy_base_dir" {
  type        = string
  description = "the path where your psoxy repo resides"

  validation {
    condition     = can(regex(".*\\/$", var.psoxy_base_dir))
    error_message = "The psoxy_base_dir value should end with a slash."
  }
  validation {
    condition     = can(regex("^[^~].*$", var.psoxy_base_dir))
    error_message = "The psoxy_base_dir value should be absolute path (not start with ~)."
  }
}

variable "google_workspace_example_user" {
  type        = string
  description = "User to impersonate for Google Workspace API calls (null for none)"
}

variable "gcp_region" {
  type        = string
  description = "Region in which to provision GCP resources, if applicable"
  default     = "us-central1"
}

variable "replica_regions" {
  type        = list(string)
  description = "List of regions in which to replicate secrets."
  default     = [
    "us-central1",
    "us-west1",
  ]
}

variable "email_domain_policy" {
  type        = string
  description = "Policy to use when sanitizing email domains. one of PRESERVE, REDACT, or HASH. see EmailDomainPolicy java enum for details."
  default     = null
}

variable "email_domain_policy_exceptions" {
  type        = list(string)
  description = "List of domains which will be excepted from the email_domain_policy."
  default     = null
}
