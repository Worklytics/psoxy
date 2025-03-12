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
  description = "Human readable reference name for this psoxy instance. Helpful for distinguishing resulting infrastructure"

  # enforce max length to avoid bucket names that are too long
  validation {
    condition     = length(var.instance_id) < 41
    error_message = "The instance_id must be at most 40 characters."
  }
}

variable "aws_account_id" {
  type        = string
  description = "id of aws account in which to provision your AWS infra"
  validation {
    condition     = can(regex("^\\d{12}$", var.aws_account_id))
    error_message = "The aws_account_id value should be 12-digit numeric string."
  }
}

# TODO: remove after 0.4.x
variable "aws_assume_role_arn" {
  type        = string
  description = "IGNORED; arn of role used to test the lambda"
  default     = null
}

# TODO: remove after 0.4.x
variable "aws_region" {
  type        = string
  default     = "us-east-1"
  description = "IGNORED; default region in which to provision your AWS infra"
}

variable "path_to_instance_ssm_parameters" {
  type        = string
  description = "path to instance config parameters in SSM Parameter Store (`null` for default, which is `PSOXY_{function_name}_`); lambda will be able to read/write params beneath this path/prefix"
  default     = null
}

# TODO : rename, this is misleading
variable "path_to_shared_ssm_parameters" {
  type        = string
  description = "path to shared global config parameters in SSM Parameter Store"
  default     = ""
}

variable "function_env_kms_key_arn" {
  type        = string
  description = "AWS KMS key ARN to use to encrypt lambda's environment. NOTE: Terraform must be authenticated as an AWS principal authorized to encrypt/decrypt with this key."
  default     = null
}

variable "logs_kms_key_arn" {
  type        = string
  description = "AWS KMS key ARN to use to encrypt lambdas' logs. NOTE: ensure CloudWatch is setup to use this key (cloudwatch principal has perms, log group in same region as key, etc) - see https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/encrypt-log-data-kms.html ."
  default     = null
}

variable "ssm_kms_key_ids" {
  type        = map(string)
  description = "KMS key IDs or ARNs that were used for encrypting SSM parameters needed by this lambda, if any."
  default     = {}
}

variable "aws_lambda_execution_role_policy_arn" {
  type        = string
  description = "*beta* The ARN of policy to attach to the lambda execution role, if you want one other than the default. (usually, AWSLambdaBasicExecutionRole)."
  default     = null
}

variable "iam_roles_permissions_boundary" {
  type        = string
  description = "*beta* ARN of the permissions boundary to attach to IAM roles created by this module."
  default     = null
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

variable "aws_role_to_assume_when_testing" {
  type        = string
  description = "ARN of role to assume when testing instance. Leave blank to use default credentials of location from which you'll run tests (which must be for a principal with sufficient privileges, or use `provision_iam_policy_for_testing`)."
  default     = null

  validation {
    condition     = var.aws_role_to_assume_when_testing == null || can(regex("^arn:aws:iam::\\d{12}:role/.*$", var.aws_role_to_assume_when_testing))
    error_message = "if provided, aws_role_to_assume_when_testing must be a valid ARN of an IAM Role"
  }
}

variable "provision_iam_policy_for_testing" {
  type        = bool
  description = "Whether to provision IAM policy and attach it to `aws_role_to_assume_when_testing`."
  default     = false
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
    pseudonymFormat                = optional(string, "JSON") # TODO: change to URL_SAFE_TOKEN in v0.5
    columnsToRedact                = optional(list(string), [])
    columnsToInclude               = optional(list(string), null)
    columnsToPseudonymize          = optional(list(string), [])
    columnsToPseudonymizeIfPresent = optional(list(string), null)
    columnsToDuplicate             = optional(map(string), {})
    columnsToRename                = optional(map(string), {})
    fieldsToTransform = optional(map(object({
      newName    = string
      transforms = optional(list(map(string)), [])
    })), {})
  })
  description = "Rules to apply to a columnar flat file during transformation"
  default = {
  }
}

variable "rules_file" {
  type        = string
  description = "path to rules file"
  default     = null
}

variable "global_parameter_arns" {
  # see https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ssm_parameter#attributes-reference
  type        = list(string)
  description = "System Manager Parameters ARNS to expose to function, expected to contain global shared parameters, like salt or encryption keys"
  default     = []
}

variable "global_secrets_manager_secret_arns" {
  type        = map(string)
  description = "Secrets Manager Secrets ARNs to expose to proxy instance, expected to contain global shared secrets, like salt or encryption keys"
  default     = {}
}

variable "memory_size_mb" {
  # See https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/lambda_function#memory_size
  type        = number
  description = "Amount of memory in MB your Lambda Function can use at runtime. Defaults to 512"
  default     = 512
}

variable "input_expiration_days" {
  type        = number
  description = "**alpha** Number of days after which objects in the bucket will expire"
  default     = 30
}

variable "sanitized_expiration_days" {
  type        = number
  description = "**alpha** Number of days after which objects in the bucket will expire"
  default     = 720
}

variable "vpc_config" {
  type = object({
    # ipv6_allowed_for_dual_stack = optional(bool, false)
    subnet_ids         = list(string)
    security_group_ids = list(string)
  })
  description = "**alpha** VPC configuration for lambda; if not provided, lambda will not be deployed in a VPC. see https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/lambda_function#vpc_config"
  default     = null
}

variable "secrets_store_implementation" {
  type        = string
  description = "one of 'aws_ssm_parameter_store' (default) or 'aws_secrets_manager'"
  default     = "aws_ssm_parameter_store"
}

variable "example_file" {
  type        = string
  description = "path to example file to use for testing, from psoxy_base_dir"
  default     = null
}

variable "instructions_template" {
  type        = string
  description = "path to setup instructions template, from psoxy_base_dir, if any"
  default     = null
}

variable "provision_bucket_public_access_block" {
  type        = bool
  description = "Whether to provision public_access_block resources on all buckets; defaults to 'true', but can be 'false' if you have organizational control policies that do this at a higher level."
  default     = true
}

variable "todos_as_local_files" {
  type        = bool
  description = "whether to render TODOs as flat files"
  default     = true
}

variable "todo_step" {
  type        = number
  description = "of all todos, where does this one logically fall in sequence"
  default     = 2
}
