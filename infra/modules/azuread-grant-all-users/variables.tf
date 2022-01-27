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
