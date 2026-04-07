variable "project_id" {
  type        = string
  description = "The project ID in which to provision parameter for rules"
}

variable "prefix" {
  type        = string
  description = "Hierarchical prefix for parameter ID, e.g. 'psoxy/gmail/'. Must end with '/'."

  validation {
    condition     = can(regex("/$", var.prefix))
    error_message = "The prefix must end with a '/'."
  }
}

variable "file_path" {
  type    = string
  default = null

  validation {
    condition     = var.file_path == null || try(fileexists(var.file_path), false)
    error_message = "The file path does not exist."
  }

  validation {
    condition     = var.file_path == null || try(endswith(var.file_path, ".yaml"), false)
    error_message = "Rules should be plain .yaml file."
  }
}

variable "content" {
  type        = string
  description = "Raw rules content string. Mutually exclusive with file_path; one of the two must be provided."
  default     = null
}

variable "instance_sa_email" {
  type        = string
  description = "The email address of the service account to use for the proxy instance that will access the rules."
}
