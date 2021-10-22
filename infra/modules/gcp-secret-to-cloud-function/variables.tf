variable "project_id" {
  type        = string
  description = "name of the gcp project"
}

variable "env_var_name" {
  type        = string
  description = "secret will be exposed to cloud function as an ENV_VAR with this name"
}

variable "secret_name" {
  type        = string
  description = "name of the secret to expose to cloud function"
}

variable "secret_version_number" {
  type        = string
  description = "number of version of the secret to expose to cloud function"
}

variable "function_name" {
  type        = string
  description = "name of cloud function that needs access to the secret"
}

variable "service_account_email" {
  type        = string
  description = "email fo the service account that the cloud function will run as"
}

variable "runtime" {
  type        = string
  default     = "java11"
  description = "runtime of the cloud function"
}
