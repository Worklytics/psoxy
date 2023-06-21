

variable "prefix" {
  type = string
}

variable "file_path" {
  type = string

  validation {
    condition     = fileexists(var.file_path)
    error_message = "The file path does not exist."
  }

  validation {
    condition     = endswith(var.file_path, ".yaml")
    error_message = "Rules should be plain .yaml file."
  }
}

variable "default_labels" {
  type        = map(string)
  description = "*Alpha* in v0.4, only respected for new resources. Labels to apply to all resources created by this configuration. Intended to be analogous to AWS providers `default_tags`."
  default     = {}
}
