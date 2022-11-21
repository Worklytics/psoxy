variable "application_object_id" {
  type        = string
  description = "Object ID of the Azure Active Directory Application to assign the federated credential"
}

variable "display_name" {
  type        = string
  description = "friendly display name to give credential"
}

variable "description" {
  type        = string
  description = "optionally, a description of the credential"
  default     = ""
}

variable "issuer" {
  type        = string
  description = "The URL of the external identity provider, which must match the issuer claim of the external token being exchanged. The combination of the values of issuer and subject must be unique on the app."
}
variable "subject" {
  type        = string
  description = "The identifier of the external software workload within the external identity provider. The combination of issuer and subject must be unique on the app."
}