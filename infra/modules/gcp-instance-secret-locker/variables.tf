variable "project_id" {
  type        = string
  description = "alphanumeric ID of gcp project"
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

variable "secret_name" {
  type        = string
  description = "name of cloud function"
  default = "OAUTH_REFRESH_TOKEN"
}

variable "updater_role_id" {
  type        = string
  description = "id of the role to update the secret"
}