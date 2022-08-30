

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

variable "aws_assume_role_arn" {
  type        = string
  description = "role arn"
}

variable "source_kind" {
  type        = string
  description = "kind of source (eg, 'gmail', 'google-chat', etc)"
}

variable "parameters" {
  # see https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ssm_parameter#attributes-reference
  type = list(object({
    name    = string
    arn     = string
    version = string
  }))
  description = "System Manager Parameters to expose to function"
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
