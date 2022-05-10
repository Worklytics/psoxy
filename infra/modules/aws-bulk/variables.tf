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

variable "instance_id" {
  type        = string
  description = "Human readable reference name for this psoxy instance. Helpful for distinguishing resulting infrastructure"
}

variable "source_kind" {
  type        = string
  default     = "hris"
  description = "Kind of the content to process"
}


variable "path_to_function_zip" {
  type        = string
  description = "path to zip archive of lambda bundle"
}

variable "function_zip_hash" {
  type        = string
  description = "hash of base64-encoded zipped lambda bundle"
}

variable "path_to_config" {
  type        = string
  description = "path to config file (usually something in ../../configs/, eg configs/gdirectory.yaml"
}

variable "api_caller_role_arn" {
  type        = string
  description = "arn of role which can be assumed to call API"
}

variable "api_caller_role_name" {
  type        = string
  description = "name of role which can be assumed to call API"
}
