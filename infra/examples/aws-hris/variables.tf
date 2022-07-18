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
  validation {
    condition     = can(regex("^\\d{12}:\\w+$", var.caller_aws_account_id))
    error_message = "The caller_aws_account_id value should be 12-digit numeric string following by the role used. Example: 914358739851:root."
  }
}

variable "caller_external_user_id" {
  type        = string
  description = "id of external user that will call proxy (eg, SA of your Worklytics instance)"
}

variable "environment_name" {
  type        = string
  description = "qualifier to append to name of project that will host your psoxy instance"
}

variable "instance_id" {
  type        = string
  description = "Human readable reference name for this psoxy instance. Helpful for distinguishing resulting infrastructure"
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
