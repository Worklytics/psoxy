variable "aws_account_id" {
  type        = string
  description = "account id that will host your proxy instance (12-digit number)"
  validation {
    condition     = can(regex("^\\d{12}$", var.aws_account_id))
    error_message = "The aws_account_id value should be 12-digit numeric string."
  }
}

variable "aws_account_with_gcp_auth" {
  default     = "939846301470:root" # Worklytics's account to which its GCP identities are federated
  type        = string
  description = "id of AWS account that will authenticate GCP SAs (eg has GCP OIDC configured); any user added to 'allowed_gcp_callers' must be auth'd by this AWS account"
  validation {
    condition     = can(regex("^\\d{12}:\\w+$", var.aws_account_with_gcp_auth))
    error_message = "The aws_account_id value should be 12-digit numeric string, followed by ':root'."
  }
}

variable "allowed_gcp_service_accounts" {
  type        = set(string) # 21-digit numeric strings
  description = "GCP callers that are allowed to access the proxy, if they're auth'd by the aws_account_with_gcp_oidc"
  default     = [] # empty set means no GCP callers are allowed, so connections from Worklytics will not work
}
