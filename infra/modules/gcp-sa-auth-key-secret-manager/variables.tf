
variable "service_account_id" {
  type        = string
  description = "ID of the service account"
}

variable "secret_project" {
  type        = string
  description = "ID of project in which to store SA key as secret"
}

variable "secret_id" {
  type        = string
  description = "ID to give secret for SA key"
}
