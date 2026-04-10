variable "project_id" {
  type        = string
  description = "The project ID in which to provision parameter for rules"
}

variable "prefix" {
  type        = string
  description = "Hierarchical prefix for parameter ID, e.g. 'psoxy/gmail/'. Must end with '/'."

  validation {
    condition     = can(regex("-$", var.prefix))
    error_message = "The prefix must end with a '-'."
  }
}

variable "content" {
  type        = string
  description = "Raw rules content string. Mutually exclusive with file_path; one of the two must be provided."
  default     = null
}