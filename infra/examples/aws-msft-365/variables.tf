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
  description = "id of Worklytics AWS account from which proxy will be called (default: 939846301470:root)"
  default     = "939846301470:root"
  validation {
    condition     = can(regex("^\\d{12}:\\w+$", var.caller_aws_account_id))
    error_message = "The caller_aws_account_id value should be 12 digits, followed by ':root'."
  }
}

variable "caller_external_user_id" {
  type        = string
  description = "id of external user that will call proxy (eg, OAuth Client ID of the service account of your Worklytics tenant)"
}

variable "msft_tenant_id" {
  type        = string
  default     = ""
  description = "ID of Microsoft tenant to connect to (req'd only if config includes MSFT connectors)"
}

variable "connector_display_name_suffix" {
  type        = string
  description = "suffix to append to display_names of connector SAs; helpful to distinguish between various ones in testing/dev scenarios"
  default     = ""
}

variable "certificate_subject" {
  type        = string
  description = "value for 'subject' passed to openssl when generation certificate (eg '/C=US/ST=New York/L=New York/CN=www.worklytics.co')"
}

variable "psoxy_basedir" {
  type        = string
  description = "the path where your psoxy repo resides"
}
