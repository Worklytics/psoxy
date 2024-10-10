variable "proxy_instance_id" {
  type        = string
  description = "friendly unique-id for Psoxy instance"
  default     = null
}

variable "connector_id" {
  type        = string
  description = "ID for connector implementation in Worklytics (to build deeplinks)"
  default     = "" # will be REQUIRED for v0.5 onwards
}

variable "host_platform_id" {
  type        = string
  description = "proxy host platform id (AWS, GCP, etc)"
  default     = "GCP"

  validation {
    condition     = contains(["AWS", "GCP"], var.host_platform_id)
    error_message = "`host_platform_id` must be one of AWS or GCP."
  }
}

variable "psoxy_endpoint_url" {
  type        = string
  description = "url of endpoint which hosts Psoxy instance"
}

variable "worklytics_host" {
  type        = string
  description = "host of worklytics instance where tenant resides. (e.g. intl.worklytics.co for prod; but may differ for dev/staging)"
  default     = "intl.worklytics.co"
}

variable "display_name" {
  type        = string
  description = "display name of connector in Worklytics."
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

variable "settings_to_provide" {
  type        = map(string)
  description = "map of values for installer to copy-paste into connection settings in Worklytics UX"
  default     = {}

  # TODO: fix these validations; logically correct, but Terraform doesn't allow validation conditions
  # to depend on values of other variables
  #  validation {
  #    condition     = var.psoxy_host_platform_id != "AWS" || contains(keys(var.settings_to_provide), "AWS Psoxy Region")
  #    error_message = "For connections to AWS deployments, must provide 'AWS Psoxy Region' in settings_to_provide."
  #  }
  #
  #  validation {
  #    condition     = var.psoxy_host_platform_id != "AWS" || contains(keys(var.settings_to_provide), "AWS Psoxy Role ARN")
  #    error_message = "For connections to AWS deployments, must provide 'AWS Psoxy Role ARN' in settings_to_provide."
  #  }
}


