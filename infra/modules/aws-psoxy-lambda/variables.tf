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

variable "region" {
  type        = string
  description = "region into which to deploy function"
  default     = "us-east-1"
}

variable "handler_class" {
  type        = string
  description = "Class to handle the request"
  default     = "co.worklytics.psoxy.Handler"
}

# TODO: remove after 0.4.x
variable "aws_assume_role_arn" {
  type        = string
  description = "IGNORED; unused role arn"
  default     = null
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

# TODO: remove after 0.4.x
variable "ssm_kms_key_ids" {
  type        = map(string)
  description = "DEPRECATED; KMS key IDs or ARNs that were used for encrypting SSM parameters needed by this lambda, if any."
  default     = {}
}

variable "kms_keys_to_allow" {
  type        = map(string)
  description = "KMS key IDs or ARNs for keys this lambda needs to use, if any."
  default     = {}
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

variable "reserved_concurrent_executions" {
  type        = number
  description = "Max number of concurrent instances for the function"
  default     = -1
}

# NOTE: currently unused; but perhaps we'll have default rules by source_kind in the future,
# so leaving it in
variable "source_kind" {
  type        = string
  description = "kind of source (eg, 'gmail', 'google-chat', etc)"
  default     = null
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
  default     = null
}

variable "environment_variables" {
  type        = map(string)
  description = "Non-sensitive values to add to functions environment variables; NOTE: will override anything in `path_to_config`"
  default     = {}
}

variable "memory_size_mb" {
  type        = number
  description = "lambda memory size in MB"
  default     = 512
}

variable "ephemeral_storage_mb" {
  type        = number
  description = "ephemeral storage size in MB"
  default     = 512 # this is the free amount; over this though it's pretty trivial cost for the use-case
}

variable "timeout_seconds" {
  type        = number
  description = "lambda timeout in seconds"
  default     = 55
}

variable "log_retention_in_days" {
  type        = number
  description = "number of days to retain logs in CloudWatch for this psoxy instance"
  default     = 7
}

variable "global_parameter_arns" {
  # see https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ssm_parameter#attributes-reference
  type        = list(string)
  description = "System Manager Parameters ARNS to expose to proxy instance, expected to contain global shared parameters, like salt or encryption keys"
  default     = []
}

variable "global_secrets_manager_secret_arns" {
  type        = map(string)
  description = "Secrets Manager Secrets ARNs to expose to proxy instance, expected to contain global shared secrets, like salt or encryption keys"
  default     = {}
}

variable "iam_roles_permissions_boundary" {
  type        = string
  description = "*beta* ARN of the permissions boundary to attach to IAM roles created by this module."
  default     = null
}

# TODO: remove after v0.4.x
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
  description = "**beta** VPC configuration for lambda; if not provided, lambda will not be deployed in a VPC. see https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/lambda_function#vpc_config"
  default     = null
}

variable "aws_lambda_execution_role_policy_arn" {
  type        = string
  description = "**beta** The ARN of policy to attach to the lambda execution role, if you want one other than the default. (usually, AWSLambdaBasicExecutionRole)."
  default     = null
}

variable "secrets_store_implementation" {
  type        = string
  description = "one of 'aws_ssm_parameter_store' (default) or 'aws_secrets_manager'"
  default     = "aws_ssm_parameter_store"
}

variable "side_output_original" {
  type        = string
  description = "**ALPHA** If provided, the function will write a copy of the original API response (unprocessed) output to this S3 URL."
  default     = null

  validation {
    condition     = var.side_output_original == null || can(regex("^s3://[a-z0-9][a-z0-9-]*[a-z0-9](/.*)?$", var.side_output_original))
    error_message = "The `side_output_original` target must be a valid S3 bucket address. Bucket names must only contain lowercase letters, numbers, and hyphens, and must start and end with a lowercase letter or number. May include a path within the bucket, in which case we highly recommend you end it with a slash (`/`)."
  }
}

variable "side_output_sanitized" {
  type        = string
  description = "**ALPHA** If provided, the function will write sanitized output to this S3 path."
  default     = null

  validation {
    condition     = var.side_output_sanitized == null || can(regex("^s3://[a-zA-Z][a-zA-Z0-9-_ ]*[a-zA-Z0-9](/.*)?$", var.side_output_sanitized))
    error_message = "The `side_output_sanitized` target must be s3 bucket address. May include a path within the bucket, in which case we highly recommend you end it with a slash (`/`)."
  }
}

variable "s3_outputs" {
  type        = list(string)
  description = "S3 urls to which the function will write sanitized output. If provided, the function will write sanitized output to these S3 URLs."
  default     = []

  validation {
    condition     = alltrue([for url in var.s3_outputs : can(regex("^s3://[a-z][a-z0-9-]*[a-z0-9](/.*)?$", url))])
    error_message = "Each `s3_outputs` target must be a valid S3 bucket address. Bucket names must only contain lowercase letters, numbers, and hyphens, and must start and end with a lowercase letter or number. May include a path within the bucket, in which case we highly recommend you end it with a slash (`/`)."
  }
}

# TODO: this is not very "inversion of control" design; arg should output the exec policy and, where we create the trigger, first create and attach the policy??
variable "sqs_trigger_queue_arns" {
  type        = list(string)
  description = "**ALPHA** ARNs of SQS queues that will trigger this lambda; if provided, perms on lambda's exec rule."
  default     = []

  validation {
    condition     = alltrue([for queue in var.sqs_trigger_queue_arns : can(regex("^arn:aws:sqs:[a-z0-9-]+:[0-9]{12}:[a-zA-Z][a-zA-Z0-9-_ ]*[a-zA-Z0-9]$", queue))])
    error_message = "Each SQS queue ARN must be a valid ARN for an AWS SQS queue."
  }
}


variable "sqs_send_queue_arns" {
  type        = list(string)
  description = "**ALPHA** ARNs of SQS queues that this lambda will SEND to; if provided, perms on lambda's exec rule."
  default     = []

  validation {
    condition     = alltrue([for queue in var.sqs_send_queue_arns : can(regex("^arn:aws:sqs:[a-z0-9-]+:[0-9]{12}:[a-zA-Z][a-zA-Z0-9-_ ]*[a-zA-Z0-9]$", queue))])
    error_message = "Each SQS queue ARN must be a valid ARN for an AWS SQS queue."
  }
}

variable "aws_kms_public_keys" {
  type        = list(string)
  description = "List of AWS KMS public keys that lambda must be able to read."
  default     = []

  validation {
    condition     = alltrue([for key in var.aws_kms_public_keys : can(regex("^arn:aws:kms:[a-z0-9-]+:[0-9]{12}:key/[a-zA-Z0-9-]+$", key))])
    error_message = "Each AWS KMS public key must be a valid ARN for an AWS KMS key."
  }
}


