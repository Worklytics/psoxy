variable "aws_account_id" {
  type        = string
  description = "id of aws account in which to provision your AWS infra"
  validation {
    condition     = can(regex("^\\d{12}$", var.aws_account_id))
    error_message = "The aws_account_id value should be 12-digit numeric string."
  }
}

variable "aws_assume_role_arn" {
  type        = string
  description = "arn of role Terraform should assume when provisioning your infra"
}

variable "aws_region" {
  type        = string
  default     = "us-east-1"
  description = "default region in which to provision your AWS infra"
}

variable "caller_aws_account_id" {
  type        = string
  description = "id of Worklytics AWS account from which proxy will be called"
  default     = "939846301470:root"
  validation {
    condition     = can(regex("^\\d{12}:\\w+$", var.caller_aws_account_id))
    error_message = "The caller_aws_account_id value should be 12-digit numeric string."
  }
}

variable "caller_external_user_id" {
  type        = string
  description = "id of external user that will call proxy (eg, SA of your Worklytics instance)"
}

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

variable "connector_display_name_suffix" {
  type        = string
  description = "suffix to append to display_names of connector SAs; helpful to distinguish between various ones in testing/dev scenarios"
  default     = ""
}
