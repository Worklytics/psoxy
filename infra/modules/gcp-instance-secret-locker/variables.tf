variable "project_id" {
  type        = string
  description = "alphanumeric ID of gcp project"
}

variable "service_account_email" {
  type        = string
  description = "email of the service account that the cloud function will run as"
}

variable "secret_id" {
  type        = string
  description = "id of the secret"
}

variable "updater_role_id" {
  type        = string
  description = "id of the role to update the secret"
}