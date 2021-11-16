variable "project_id" {
  type        = string
  description = "name of the gcp project"
}

variable "function_name" {
  type        = string
  description = "name of cloud function"
}

variable "source_kind" {
  type        = string
  description = "kind of source (eg, 'gmail', 'google-chat', etc)"
}

variable "service_account_email" {
  type        = string
  description = "email of the service account that the cloud function will run as"
}

variable "secret_bindings" {
  type = map(object({
    secret_name    = string
    version_number = string
  }))
  description = "map of Secret Manager Secrets to expose to cloud function (ENV_VAR_NAME --> resource ID of GCP secret)"
}
