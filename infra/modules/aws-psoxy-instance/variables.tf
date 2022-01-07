
variable "region" {
  type        = string
  description = "region into which to deploy function"
  default     = "us-east-1"
}

variable "function_name" {
  type        = string
  description = "name of function"
}

variable "source_kind" {
  type        = string
  description = "kind of source (eg, 'gmail', 'google-chat', etc)"
}

variable "secret_bindings" {
  type = map(object({
    secret_name    = string
    version_number = string
  }))
  description = "map of Secret Manager Secrets to expose to cloud function (ENV_VAR_NAME --> resource ID of secret)"
}

variable "api_gateway" {
  type        = object({
    id = string
    arn = string
    api_endpoint = string
  })
  description = "API gateway behind which proxy instance should sit"
}

variable "execution_role_arn" {
  type        = string
  description = "arn of execution role for function"
}

variable "path_to_function_zip" {
  type        = string
  description = "path to lambda zip"
}

variable "path_to_config" {
  type        = string
  description = "path to config file (usually someting in ../../configs/, eg configs/gdirectory.yaml"
}
