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

variable "required_resources" {
  type        = list(object({
    id   = string,
    type = string # usually 'Role', but possibly 'Scope'
  }))

  description  = "list of Azure AD OAuth2 permissions that connector requires"
}
