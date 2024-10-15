variable "proxy_instance_id" {
  type        = string
  description = "friendly unique-id for proxy instance"
  default     = null
}

variable "worklytics_host" {
  type        = string
  description = "host of worklytics instance where tenant resides. (e.g. intl.worklytics.co for prod; but may differ for dev/staging)"
  default     = "intl.worklytics.co"
}

variable "display_name" {
  type        = string
  description = "display name of connector in Worklytics"
}

variable "aws_role_arn" {
  type        = string
  description = "ARN of role to that Worklytics should assume when connecting to proxy."
}

variable "aws_region" {
  type        = string
  description = "AWS region in which proxy lambda is deployed"
}

variable "proxy_endpoint_url" {
  type        = string
  description = "URL of endpoint which hosts Psoxy instance, for API connectors."
  default     = null
}

variable "bucket_name" {
  type        = string
  description = "Name of S3 bucket from which to retrieve sanitized data, for Bulk connectors."
  default     = null
}

variable "connector_id" {
  type        = string
  description = "ID for connector implementation in Worklytics (to build deeplinks)"
  default     = "" # will be REQUIRED for v0.5 onwards
}

variable "connector_settings_to_provide" {
  type        = map(string)
  description = "Map of additional, connector-specific settings to provide to Worklytics when creating connection."
  default     = {}
}

variable "todos_as_local_files" {
  type        = bool
  description = "whether to render TODOs as flat files"
  default     = true
}

variable "todo_step" {
  type        = number
  description = "of all todos, where does this one logically fall in sequence"
  default     = 3
}
