variable "secrets" {
  type = map(
    object({
      value               = string
      description         = optional(string, "")
      value_managed_by_tf = optional(bool, true) # if value will be managed by Terraform, or some outside process
      sensitive           = optional(bool, true) # if value is to be handled as sensitive
    })
  )
}

variable "path" {
  type        = string
  description = "secrets will be created under this path (prefix); be sure to include trailing `/` if needed."
  default     = ""
}

variable "kms_key_id" {
  type        = string
  description = "KMS key ID or ARN to use for encrypting secrets. If not provided, secrets will be encrypted by SSM with its keys (controlled by AWS)."
  default     = null
}
