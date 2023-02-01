variable "tenant_id" {
  type        = string
  description = "ID of the Azure AD tenant in which to provision the connector"
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

variable "required_app_roles" {
  type        = list(string)
  description = "list of names of Microsoft Graph App Roles required by connector"
}

variable "required_oauth2_permission_scopes" {
  type        = list(string)
  description = "list of names of Microsoft Graph OAuth2 Permissions required by connector"
}

variable "owners" {
  type = list(string)
  description = "list of object ids to be set as owner of the application"
  default = []
}