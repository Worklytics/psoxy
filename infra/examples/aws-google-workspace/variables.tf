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

variable "aws_account_with_gcp_auth" {
  default     = "939846301470:root" # Worklytics's account to which its GCP identities are federated
  type        = string
  description = "id of AWS account that will authenticate GCP SAs (eg has GCP OIDC configured); any user added to 'allowed_gcp_callers' must be auth'd by this AWS account"
  validation {
    condition     = can(regex("^\\d{12}:\\w+$", var.aws_account_with_gcp_auth))
    error_message = "The aws_account_id value should be 12-digit numeric string, followed by ':root'."
  }
}

variable "allowed_gcp_service_accounts" {
  type        = set(string) # 21-digit numeric strings
  description = "GCP callers that are allowed to access the proxy, if they're auth'd by the aws_account_with_gcp_oidc"
  default     = [] # empty set means no GCP callers are allowed, so connections from Worklytics will not work
}

variable "gcp_project_id" {
  type        = string
  description = "id of GCP project that will host psoxy instance"
}

variable "environment_name" {
  type        = string
  description = "qualifier to append to name of project that will host your psoxy instance"
  default     = ""
}

variable "gcp_folder_id" {
  type        = string
  description = "optionally, numeric ID of folder into which to provision it"
  default     = null

  validation {
    condition     = can(regex("^\\d{12}$", var.gcp_folder_id))
    error_message = "The gcp_folder_id value should be 12-digit numeric string."
  }
}

variable "gcp_billing_account_id" {
  type        = string
  description = "billing account ID; needed to create the project"
}

variable "connector_display_name_suffix" {
  type        = string
  description = "suffix to append to display_names of connector SAs; helpful to distinguish between various ones in testing/dev scenarios"
  default     = ""
}

variable "path_to_java" {
  type        = string
  description = "path from root terraform configuration to psoxy's java source"
  default     = "../../../java"
}
