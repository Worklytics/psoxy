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
