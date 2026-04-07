

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
    condition     = var.file_path == null || try(endswith(var.file_path, ".yaml"), false)
    error_message = "Rules should be plain .yaml file."
  }
}

variable "content" {
  type        = string
  description = "Raw rules content string. Mutually exclusive with file_path; one of the two must be provided."
  default     = null
}
