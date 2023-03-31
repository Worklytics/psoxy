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
  description = "arn of role used to test the lambda"
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

variable "path_to_instance_ssm_parameters" {
  type        = string
  description = "path to instance config parameters in SSM Parameter Store (`null` for default, which is `PSOXY_{function_name}_`); lambda will be able to read/write params beneath this path/prefix"
  default     = null
}

variable "ssm_kms_key_ids" {
  type        = map(string)
  description = "KMS key IDs or ARNs that were used for encrypting SSM parameters needed by this lambda, if any."
  default     = {}
}

variable "log_retention_days" {
  type        = number
  description = "number of days to retain logs in CloudWatch for this psoxy instance"
  default     = 7
}

# NOTE: currently unused; but perhaps we'll have default rules by source_kind in the future,
# so leaving it in
variable "source_kind" {
  type        = string
  default     = "hris"
  description = "kind of source of the content to process"
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
  default     = null
}

variable "api_caller_role_arn" {
  type        = string
  description = "DEPRECATED; arn of role which can be assumed to call API"
  default     = null
}

variable "api_caller_role_name" {
  type        = string
  description = "DEPRECATED; name of role which can be assumed to call API"
  default     = null
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
    # NOTE: use `optional()` in variables.tf of modules that wrap this one, but omit the default
    # value so that the one here prevails (unless should really be different for your use-case)
    pseudonymFormat       = optional(string, "JSON") # TODO: change to URL_SAFE_TOKEN in v0.5
    columnsToRedact       = optional(list(string), [])
    columnsToInclude      = optional(list(string), null)
    columnsToPseudonymize = optional(list(string), [])
    columnsToDuplicate    = optional(map(string), {})
    columnsToRename       = optional(map(string), {})
  })
  description = "Rules to apply to a columnar flat file during transformation"
  default = {
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
  type        = number
  description = "Amount of memory in MB your Lambda Function can use at runtime. Defaults to 512"
  default     = 512
}

variable "example_file" {
  type        = string
  description = "path to example file to use for testing, from psoxy_base_dir"
  default     = null
}

variable "todo_step" {
  type        = number
  description = "of all todos, where does this one logically fall in sequence"
  default     = 2
}
