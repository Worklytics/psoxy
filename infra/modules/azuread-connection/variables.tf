variable "tenant_id" {
  type        = string
  description = "ID of the Azure AD tenant in which to provision the connector"
}

variable "display_name" {
  type        = string
  description = "friendly display name to give connector. ignored if `existing_app_object_id` is provided."
  default     = null
}

variable "description" {
  type        = string
  description = "optionally, a description of the connector. ignored if `existing_app_object_id` is provided."
  default     = ""
}

variable "required_app_roles" {
  type        = list(string)
  description = "list of names of Microsoft Graph App Roles required by connector. ignored if `existing_app_object_id` is provided."
}

variable "required_oauth2_permission_scopes" {
  type        = list(string)
  description = "list of names of Microsoft Graph OAuth2 Permissions required by connector. ignored if `existing_app_object_id` is provided."
}

variable "owners" {
  type        = set(string)
  description = "list of object ids to be set as owner of the application. ignored if `existing_app_object_id` is provided."
  default     = []
}

variable "existing_app_object_id" {
  type        = string
  description = "if provided, the app corresponding to this object id will be used instead of creating a new one. User must ensure that roles/scopes are appropriate for the connector"
  default     = null
}
