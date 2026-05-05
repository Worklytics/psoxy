variable "project_id" {
  type        = string
  description = "ID of the GCP project in which to provision the connector"
}

variable "instance_id" {
  type        = string
  description = "identifier that uniquely identifies this connector instance within environment"
  default     = null
}

variable "connector_service_account_id" {
  type        = string
  description = "string id to give Service Account that personifies connector"
}

variable "display_name" {
  type        = string
  description = "friendly display name to give connector"
}

variable "description" {
  type        = string
  description = "optionally, a description of the connector"
  default     = ""
}

variable "apis_consumed" {
  type        = list(string)
  description = "APIs to be used for this connection (Eg, 'gmail.googleapis.com')"
}

variable "oauth_scopes_needed" {
  type        = list(string)
  description = "oauth scopes that connector SA must be granted"
  default     = []
}

variable "todos_as_local_files" {
  type        = bool
  description = "[DEPRECATED - local_file resources moved to root module; this has no effect within the module. TODO: remove in 0.7] whether to render TODOs as flat files"
  default     = true
}

variable "todo_step" {
  type        = number
  description = "[DEPRECATED - todo ordering now handled at root module level; this has no effect within the module. TODO: remove in 0.7] of all todos, where does this one logically fall in sequence"
  default     = 1
}

