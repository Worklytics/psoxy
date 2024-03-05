variable "psoxy_instance_id" {
  type        = string
  description = "friendly unique-id for Psoxy instance"
}

variable "application_id" {
  type        = string
  description = "object ID of the Azure AD application to authorize"
}

variable "oauth2_permission_scopes" {
  type        = list(string)
  description = "names of OAuth2 Permission Scopes to grant to application for Microsoft Graph"
}

variable "app_roles" {
  type        = list(string)
  description = "names of Application Roles to grant to application for Microsoft Graph"
}

variable "application_name" {
  type        = string
  description = "friendly name for application"
}

variable "todos_as_local_files" {
  type        = bool
  description = "whether to render TODOs as flat files"
  default     = true
}

variable "todo_step" {
  type        = number
  description = "of all todos, where does this one logically fall in sequence"
  default     = 1
}
