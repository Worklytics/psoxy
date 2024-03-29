variable "project_id" {
  type        = string
  description = "The project ID in which to provision secret for rules"
}

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

variable "instance_sa_email" {
  type        = string
  description = "The email address of the service account to use for the proxy instance that will access the rules."
}
