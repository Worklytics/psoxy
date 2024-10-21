variable "application_id" {
  type        = string
  description = "The resource ID (Object ID) of the Azure Active Directory Application to assign the federated credential"
}
# NOTE: naming of above variable aligns to argument name on azuread_application_federated_identity_credential
# see: https://registry.terraform.io/providers/hashicorp/azuread/latest/docs/resources/application_federated_identity_credential

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

variable "audience" {
  type = string
  # This is the recommended value from MSFT, as it is what AAD expects to be in the "aud"
  # property token value
  # See https://learn.microsoft.com/en-us/azure/active-directory/develop/workload-identity-federation-create-trust?pivots=identity-wif-apps-methods-azp#important-considerations-and-restrictions
  default = "api://AzureADTokenExchange"
}

variable "subject" {
  type        = string
  description = "The identifier of the external software workload within the external identity provider. The combination of issuer and subject must be unique on the app."
}
