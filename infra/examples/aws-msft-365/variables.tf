variable "aws_account_id" {
  type        = string
  description = "id of aws account in which to provision your AWS infra"
  validation {
    condition     = can(regex("^\\d{12}$", var.aws_account_id))
    error_message = "The aws_account_id value should be 12-digit numeric string."
  }
}

variable "aws_assume_role_arn" {
  type        = string
  description = "arn of role Terraform should assume when provisioning your infra"
}

variable "aws_region" {
  type        = string
  default     = "us-east-1"
  description = "default region in which to provision your AWS infra"
}

variable "caller_gcp_service_account_ids" {
  type        = list(string)
  description = "ids of GCP service accounts allowed to send requests to the proxy (eg, unique ID of the SA of your Worklytics instance)"
  default     = []

  validation {
    condition = alltrue([
      for i in var.caller_gcp_service_account_ids : (length(regexall("^\\d{21}$", i)) > 0)
    ])
    error_message = "The values of caller_gcp_service_account_ids should be 21-digit numeric strings."
  }
}

variable "caller_aws_arns" {
  type        = list(string)
  description = "ARNs of AWS accounts allowed to send requests to the proxy (eg, arn:aws:iam::914358739851:root)"
  default     = []

  validation {
    condition = alltrue([
      for i in var.caller_aws_arns : (length(regexall("^arn:aws:iam::\\d{12}:\\w+$", i)) > 0)
    ])
    error_message = "The values of caller_aws_arns should be AWS Resource Names, something like 'arn:aws:iam::914358739851:root'."
  }
}
variable "msft_tenant_id" {
  type        = string
  default     = ""
  description = "ID of Microsoft tenant to connect to (req'd only if config includes MSFT connectors)"
}

variable "connector_display_name_suffix" {
  type        = string
  description = "suffix to append to display_names of connector SAs; helpful to distinguish between various ones in testing/dev scenarios"
  default     = ""
}

variable "certificate_subject" {
  type        = string
  description = "value for 'subject' passed to openssl when generation certificate (eg '/C=US/ST=New York/L=New York/CN=www.worklytics.co')"
}

variable "psoxy_base_dir" {
  type        = string
  description = "the path where your psoxy repo resides. Preferably a full path, /home/user/repos/, avoid tilde (~) shortcut to $HOME"
  default     = "../../../"

  validation {
    condition     = can(regex(".*\\/$", var.psoxy_base_dir))
    error_message = "The psoxy_base_dir value should end with a slash."
  }
}

variable "pseudonymize_app_ids" {
  type        = string
  description = "if set, will set value of PSEUDONYMIZE_APP_IDS environment variable to this value for all sources"
  default     = false
}
