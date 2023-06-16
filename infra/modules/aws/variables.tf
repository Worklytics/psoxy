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

  validation {
    condition = alltrue([
      for i in var.caller_aws_arns : (length(regexall("^arn:aws:iam::\\d{12}:((role|user)\\/)?\\w+$", i)) > 0)
    ])
    error_message = "The values of caller_aws_arns should be AWS Resource Names, something like 'arn:aws:iam::123123123123:root'."
  }
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
