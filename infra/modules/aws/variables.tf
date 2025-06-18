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
  description = "IGNORED - value taken from provider; region into which to deploy function"
  default     = "us-east-1"
}

variable "psoxy_base_dir" {
  type        = string
  description = "the path where your psoxy repo resides"
  default     = "../../.."

  validation {
    condition     = fileexists(format("%sjava/pom.xml", var.psoxy_base_dir))
    error_message = "The psoxy_base_dir value should be a path to a directory containing java/pom.xml."
  }
}

variable "deployment_bundle" {
  type        = string
  description = "path to deployment bundle to use (if not provided, will build one)"
  default     = null
}

variable "force_bundle" {
  type        = bool
  description = "whether to force build of deployment bundle, even if it already exists"
  default     = false
}

variable "caller_gcp_service_account_ids" {
  type        = list(string)
  description = "ids of GCP OAuth Clients (service accounts) allowed to send requests to the proxy (eg, unique ID of the SA of your Worklytics instance)"
  default     = []

  validation {
    condition = alltrue([
      for i in var.caller_gcp_service_account_ids : (length(regexall("^\\d{21}$", i)) > 0)
    ])
    error_message = "The values of caller_gcp_service_account_ids should be 21-digit numeric strings."
  }
}

variable "caller_aws_arns" {
  type        = list(string)
  description = "ARNs of AWS accounts allowed to send requests to the proxy (eg, arn:aws:iam::123123123123:root)"
  default     = []


  # in theory, can/should enforce validation here, but is less flexible; if it's WRONG, customer
  # must wait for next release of module to have it corrected
  #  validation {
  #    condition = alltrue([
  #      # see https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_iam-quotas.html
  #      # sources suggest limit of 64 chars for role names, but not clear if that includes paths so not checking it
  #      for i in var.caller_aws_arns : (length(regexall("^arn:aws:iam::\\d{12}:((role|user)\\/)?[A-Za-z0-9/=,.@_-]+$", i)) > 0)
  #    ])
  #    error_message = "The values of caller_aws_arns should be AWS Resource Names, something like 'arn:aws:iam::123123123123:root', 'arn:aws:iam::123123123123:user/ExampleUser', 'arn:aws:iam:123123123123:role/TestRole'", # for testing; can remove once prod-ready
  #  }
}

variable "psoxy_version" {
  type        = string
  description = "IGNORED; version of psoxy to deploy"
  default     = null
}

variable "install_test_tool" {
  type        = bool
  description = "whether to install the test tool (can be 'false' if Terraform not running from a machine where you intend to run tests of your Psoxy deployment)"
  default     = true
}


variable "deployment_id" {
  type        = string
  description = "unique identifier for this deployment (used to differentiate resource names)"
  default     = "Psoxy"
}

# TODO: remove in v0.5
variable "rest_function_name_prefix" {
  type        = string
  description = "DEPRECATED - use `api_function_name_prefix`; prefix for REST function names"
  default     = null
}

# TODO : change default in v0.5, or remove; should be based on deployment_id
variable "api_function_name_prefix" {
  type        = string
  description = "prefix for API function names"
  default     = "psoxy-"
}

variable "use_api_gateway_v2" {
  type        = bool
  description = "whether to use API Gateway (v2); if not lambdas exposed via function URLs."
  default     = false
}

variable "logs_kms_key_arn" {
  type        = string
  description = "AWS KMS key ARN to use to encrypt lambdas' logs. NOTE: ensure CloudWatch is setup to use this key (cloudwatch principal has perms, log group in same region as key, etc) - see https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/encrypt-log-data-kms.html ."
  default     = null
}

variable "iam_roles_permissions_boundary" {
  type        = string
  description = "*beta* ARN of the permissions boundary to attach to IAM roles created by this module."
  default     = null
}

variable "enable_webhook_testing" {
  type        = bool
  description = "whether to provision/enable webhook testing functionality"
  default     = true
}
