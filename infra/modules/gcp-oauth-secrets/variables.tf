variable "project_id" {
  type        = string
  description = "name of the gcp project"
}

variable "secret_name" {
  type        = string
  description = "name of cloud function"
}

variable "service_account_email" {
  type        = string
  description = "email of the service account that the cloud function will run as"
}

variable "path_prefix" {
  type        = string
  description = "A prefix to add to the secret path."
  default     = ""
}

variable "default_labels" {
  type        = map(string)
  description = "*Alpha* in v0.4, only respected for new resources. Labels to apply to all resources created by this configuration. Intended to be analogous to AWS providers `default_tags`."
  default     = {}
}
