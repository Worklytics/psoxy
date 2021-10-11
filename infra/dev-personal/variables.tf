variable "project_id" {
  type        = string
  description = "id of GCP project that will host psoxy instance"
}

variable "environment_name" {
  type        = string
  description = "qualifier to append to name of project that will host your psoxy instance"
}

variable "folder_id" {
  type        = string
  description = "optionally, a folder into which to provision it"
  default     = null
}

variable "billing_account_id" {
  type        = string
  description = "billing account ID; needed to create the project"
}

variable "worklytics_sa_emails" {
  type        = list(string)
  description = "service accounts for your organization's Worklytics instances (list supported for test/dev scenarios)"
}
