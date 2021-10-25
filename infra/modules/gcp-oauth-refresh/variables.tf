variable "project_id" {
  type        = string
  description = "name of the gcp project"
}

variable "function_name" {
  type        = string
  description = "name of cloud function"
}

variable "service_account_email" {
  type        = string
  description = "email fo the service account that the cloud function will run as"
}
