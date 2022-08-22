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

variable "region" {
  type        = string
  description = "region into which to deploy function"
  default     = "us-central1"
}

variable "bucket_prefix" {
  type        = string
  description = "Prefix for buckets. Buckets will be created adding a suffix -import and -processed to this prefix"
}

variable "bucket_location" {
  type        = string
  description = "Location where buckets will be deployed"
  default     = "US"
}

variable "source_kind" {
  type        = string
  default     = "hris"
  description = "Kind of the content to process"
}

variable "psoxy_base_dir" {
  type        = string
  description = "the path where your psoxy repo resides. Preferably a full path, /home/user/repos/, avoid tilde (~) shortcut to $HOME"
  default     = "../../../"

  validation {
    condition     = can(regex(".*\\/$", var.psoxy_base_dir))
    error_message = "The psoxy_base_dir value should end with a slash."
  }
}
