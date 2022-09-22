variable "aws_account_id" {
  type        = string
  description = "id of aws account in which to provision your AWS infra"
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

variable "function_name" {
  type        = string
  description = "name of function"
}

variable "handler_class" {
  type        = string
  description = "Class to handle the request"
  default     = "co.worklytics.psoxy.Handler"
}

variable "reserved_concurrent_executions" {
  type        = number
  description = "Max number of concurrent instances for the function"
  default     = null # meaning no reserved concurrency
}

variable "aws_assume_role_arn" {
  type        = string
  description = "role arn"
}

variable "source_kind" {
  type        = string
  description = "kind of source (eg, 'gmail', 'google-chat', etc)"
}

variable "path_to_repo_root" {
  type        = string
  description = "the path where your psoxy repo resides"
  default     = "../../.."
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
  description = "path to config file (usually someting in ../../configs/, eg configs/gdirectory.yaml"
}

variable "api_caller_role_arn" {
  type        = string
  description = "arn of role which can be assumed to call API"
}

variable "example_api_calls" {
  type        = list(string)
  description = "example endpoints that can be called via proxy"
}

variable "example_api_calls_user_to_impersonate" {
  type        = string
  description = "if example endpoints require impersonation of a specific user, use this id"
  default     = null
}

variable "environment_variables" {
  type        = map(string)
  description = "Non-sensitive values to add to functions environment variables; NOTE: will override anything in `path_to_config`"
  default     = {}
}

# TODO: values passed around but used yet. Intended use is to grant access only to those listed here
# TODO (cont): Right now lambdas have read grants to all ssm parameters
variable "parameters" {
  # see https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ssm_parameter#attributes-reference
  type = list(object({
    name    = string
    arn     = string
    version = string
  }))
  description = "System Manager Parameters to expose to function"
}
