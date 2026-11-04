

variable "prefix" {
  type = string
}

variable "file_path" {
  type    = string
  default = null

  validation {
    condition     = var.file_path == null || try(fileexists(var.file_path), false)
    error_message = "The file path does not exist."
  }

  validation {
    condition = var.file_path == null || (
      try(endswith(var.file_path, ".yaml"), false) || try(endswith(var.file_path, ".yml"), false)
    )
    error_message = "Rules should be a plain .yaml or .yml file."
  }
}

variable "content" {
  type        = string
  description = "Raw rules content string. Mutually exclusive with file_path; one of the two must be provided."
  default     = null
}

variable "remote_resource_bucket" {
  type        = string
  description = "When rules exceed the SSM size limit, store them at {remote_resource_instance_path}/rules.yaml in this bucket instead."
  default     = null
}

variable "remote_resource_instance_path" {
  type        = string
  description = "Path prefix within remote_resource_bucket for this connector's rules.yaml object."
  default     = null
}

variable "description" {
  type        = string
  description = "Optional SSM parameter description."
  default     = null
}
