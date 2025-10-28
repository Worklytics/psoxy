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

variable "handler_class" {
  type        = string
  description = "Class to handle the request"
  default     = "co.worklytics.psoxy.Handler"
}

variable "max_instance_count" {
  type        = number
  description = "Maximum number of concurrent instances for the function. If null, no limit is set."
  default     = null
}

variable "reserved_concurrent_executions" {
  type        = number
  description = "DEPRECATED: use max_instance_count instead. Max number of concurrent instances for the function"
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

variable "example_api_requests" {
  type = list(object({
    method       = optional(string, "GET")
    path         = string
    content_type = optional(string, "application/json")
    body         = optional(string, null)
  }))
  description = "example API requests with method, content_type and body parameters that can be called via proxy"
  default     = []
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

variable "global_secrets_manager_secret_arns" {
  type        = map(string)
  description = "Secrets Manager Secrets ARNs to expose to proxy instance, expected to contain global shared secrets, like salt or encryption keys"
  default     = {}
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

variable "vpc_config" {
  type = object({
    # ipv6_allowed_for_dual_stack = optional(bool, false)
    subnet_ids         = list(string)
    security_group_ids = list(string)
  })
  description = "**alpha** VPC configuration for lambda; if not provided, lambda will not be deployed in a VPC. see https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/lambda_function#vpc_config"
  default     = null
}

variable "memory_size_mb" {
  # See https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/lambda_function#memory_size
  type        = number
  description = "Amount of memory in MB your Lambda Function can use at runtime. Defaults to 512"
  default     = 512
}

variable "aws_lambda_execution_role_policy_arn" {
  type        = string
  description = "**beta** The ARN of policy to attach to the lambda execution role, if you want one other than the default. (usually, AWSLambdaBasicExecutionRole)."
  default     = null
}

variable "api_gateway_v2" {
  type = object({
    name             = string
    id               = string
    execution_arn    = string
    api_endpoint     = string
    stage_invoke_url = string # augmentation to apigatewayv2 resource; adding the invoke url from the stage
  })
  description = "the API Gateway v2 instance via which to expose this instance, if any (leave `null` for none)"
  default     = null
}

variable "http_methods" {
  type        = list(string)
  description = "HTTP methods to expose; has no effect unless api_gateway is also provided"
  default     = ["HEAD", "GET", "POST"]
}

variable "secrets_store_implementation" {
  type        = string
  description = "one of 'aws_ssm_parameter_store' (default) or 'aws_secrets_manager'"
  default     = "aws_ssm_parameter_store"
}

variable "side_output_original" {
  type = object({
    bucket          = optional(string, null),     # if omitted, a bucket will be created
    allowed_readers = optional(list(string), []), # a list of ARNs of aws principals that should be allowed to read the bucket
  })
  description = "**ALPHA** Configures the side output to create. If not bucket provided, one will be provisioned."
  default     = null
}

variable "side_output_sanitized" {
  type = object({
    bucket          = optional(string, null),     # if omitted, a bucket will be created
    allowed_readers = optional(list(string), []), # a list of ARNs of aws principals that should be allowed to read the bucket
  })
  description = "**ALPHA** Configures the side output to create. If not bucket provided, one will be provisioned."
  default     = null
}


variable "todos_as_local_files" {
  type        = bool
  description = "whether to render TODOs as flat files"
  default     = true
}

variable "enable_async_processing" {
  type        = bool
  description = "whether to enable async processing for this connector"
  default     = false
}

variable "todo_step" {
  type        = number
  description = "of all todos, where does this one logically fall in sequence"
  default     = 2
}

