variable "aws_account_id" {
  type        = string
  description = "id of aws account in which to provision your AWS infra"
  validation {
    condition     = can(regex("^\\d{12}$", var.aws_account_id))
    error_message = "The aws_account_id value should be 12-digit numeric string."
  }
}

variable "aws_region" {
  type        = string
  default     = "us-east-1"
  description = "default region in which to provision your AWS infra"
}

variable "instance_id" {
  type        = string
  description = "Human readable reference name for this psoxy instance. Helpful for distinguishing resulting infrastructure"

  # enforce max length to avoid bucket names that are too long
  validation {
    condition     = length(var.instance_id) < 41
    error_message = "The instance_id must be at most 40 characters."
  }
}

variable "input_bucket" {
  type        = string
  description = "name S3 input bucket"
}

variable "path_to_function_zip" {
  type        = string
  description = "path to zip archive of lambda bundle"
}

variable "function_zip_hash" {
  type        = string
  description = "hash of base64-encoded zipped lambda bundle"
}

variable "sanitized_accessor_role_names" {
  type        = list(string)
  description = "list of names of AWS IAM Roles which should be able to access the sanitized (output) bucket"
}

variable "psoxy_base_dir" {
  type        = string
  description = "the path where your psoxy repo resides"
  default     = "../../.."
}

variable "environment_variables" {
  type        = map(string)
  description = "Non-sensitive values to add to functions environment variables; NOTE: will override anything in `path_to_config`"
  default     = {}
}

variable "rules" {
  type = object({
    pseudonymFormat       = string
    columnsToRedact       = list(string)
    columnsToInclude      = list(string)
    columnsToPseudonymize = list(string)
    columnsToDuplicate    = map(string)
    columnsToRename       = map(string)
  })
  description = "Rules to apply to a columnar flat file during transformation"
  default = {
    pseudonymFormat       = "URL_SAFE_TOKEN"
    columnsToRedact       = []
    columnsToInclude      = null
    columnsToPseudonymize = []
    columnsToDuplicate    = {}
    columnsToRename       = {}
  }
}

variable "global_parameter_arns" {
  # see https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ssm_parameter#attributes-reference
  type        = list(string)
  description = "System Manager Parameters ARNS to expose to function, expected to contain global shared parameters, like salt or encryption keys"
  default     = []
}

variable "memory_size_mb" {
  # See https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/lambda_function#memory_size
  type = string
  description = "Amount of memory in MB your Lambda Function can use at runtime. Defaults to 512"
  default = 512
}