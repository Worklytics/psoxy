variable "aws_account_id" {
  type        = string
  description = "account id that will host your proxy instance (12-digit number)"
  validation {
    condition     = can(regex("^\\d{12}$", var.aws_account_id))
    error_message = "The aws_account_id value should be 12-digit numeric string."
  }
}

variable "region" {
  type        = string
  description = "region into which to deploy function"
  default     = "us-east-1"
}

variable "caller_aws_account_id" {
  type        = string
  description = "id of worklytics sa"
  default     = "914358739851"
  validation {
    condition     = can(regex("^\\d{12}:\\w+$", var.caller_aws_account_id))
    error_message = "The aws_account_id value should be 12-digit numeric string, followed by ':root'."
  }
}

#eg "780C7DE5BBF9127"
variable "caller_external_user_id" {
  type        = string
  description = "id of service account that will call proxy (eg, unique ID of the SA of your Worklytics instance)"
  validation {
    condition     = can(regex("^\\d{21}$", var.caller_external_user_id))
    error_message = "The caller_external_user_id value should be 21-digit numeric string."
  }
}

