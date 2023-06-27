variable "environment_name" {
  type        = string
  description = "friendly qualifier to distinguish resources created by this terraform configuration other Terraform deployments, (eg, 'prod', 'dev', etc)"

  validation {
    condition     = can(regex("^[a-zA-Z][a-zA-Z0-9-_ ]*[a-zA-Z0-9]$", var.environment_name))
    error_message = "The `environment_name` must start with a letter, can contain alphanumeric characters, hyphens, underscores, and spaces, and must end with a letter or number."
  }

  validation {
    condition     = !can(regex("^(?i)(aws|ssm)", var.environment_name))
    error_message = "The `environment_name` cannot start with 'aws' or 'ssm', as this will name your AWS resources with prefixes that displease the AMZN overlords."
  }
}

variable "instance_id" {
  type        = string
  description = "Human readable reference name for proxy instance, that uniquely identifies it given `environment_name`. Helpful for distinguishing resulting infrastructure"
}

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
  description = "IGNORED; inferred from provider"
  default     = "us-east-1"
}

variable "path_to_instance_ssm_parameters" {
  type        = string
  description = "path to instance config parameters in SSM Parameter Store (`null` for default, which is `PSOXY_{function_name}_`); lambda will be able to read/write params beneath this path/prefix"
  default     = null
}

variable "function_env_kms_key_arn" {
  type        = string
  description = "AWS KMS key ARN to use to encrypt lambda's environment. NOTE: Terraform must be authenticated as an AWS principal authorized to encrypt/decrypt with this key."
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

# TODO: remove after 0.4.x
variable "aws_assume_role_arn" {
  type        = string
  description = "IGNORED; arn of role used to test the lambda"
  default     = null
}

variable "source_kind" {
  type        = string
  description = "kind of source (eg, 'gmail', 'google-chat', etc)"
}

variable "identifier_scope_id" {
  type        = string
  description = "DEPRECATED; will be removed in v0.5.x"
  default     = null
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
  description = "DEPRECATED; path to config file (usually someting in ../../configs/, eg configs/gdirectory.yaml"
  default     = null
}

variable "target_host" {
  type        = string
  description = "The target host to which to forward requests."
  default     = null # for v0.4, this is optional; assumed to be in config if not defined here
}

variable "source_auth_strategy" {
  type        = string
  description = "The authentication strategy to use when connecting to the source."
  default     = null # for v0.4, this is optional; assumed to be in config if not defined here
}

variable "oauth_scopes" {
  type        = list(string)
  description = "The OAuth scopes to use when connecting to the source."
  default     = []
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

variable "global_parameter_arns" {
  # see https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ssm_parameter#attributes-reference
  type        = list(string)
  description = "System Manager Parameters ARNS to expose to function, expected to contain global shared parameters, like salt or encryption keys"
  default     = []
}

# remove after v0.4.x
variable "function_parameters" {
  type = list(object({
    name     = string
    writable = bool
  }))
  description = "IGNORED; Parameter names and expected grant to create for function"
  default     = []
}

variable "todo_step" {
  type        = number
  description = "of all todos, where does this one logically fall in sequence"
  default     = 2
}

variable "memory_size_mb" {
  # See https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/lambda_function#memory_size
  type        = number
  description = "Amount of memory in MB your Lambda Function can use at runtime. Defaults to 512"
  default     = 512
}
