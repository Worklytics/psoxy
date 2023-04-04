

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
