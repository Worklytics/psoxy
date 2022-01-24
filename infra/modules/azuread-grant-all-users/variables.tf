
variable "required_resource_access" {
  type        = list(object({
    id   = string,
    type = string # usually 'Role', but possibly 'Scope'
  }))

  description  = "list of Azure AD OAuth2 permissions that connector requires"
}

variable "application_id" {
  type        = string
  description = "object ID of the Azure AD application to authorize"
}
