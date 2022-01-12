
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

variable "parameter_bindings" {
  type = map(object({
    name    = string
  }))
  description = "map of System Manager Parameters to expose to function"
}

variable "api_gateway" {
  type        = object({
    id = string
    arn = string
    api_endpoint = string
  })
  description = "API gateway behind which proxy instance should sit"
}

variable "path_to_function_zip" {
  type        = string
  description = "path to lambda zip"
}

variable "path_to_config" {
  type        = string
  description = "path to config file (usually someting in ../../configs/, eg configs/gdirectory.yaml"
}
