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
  description = "if provided, arn of role Terraform should assume for test commands"
  default     = ""
}

variable "aws_region" {
  type        = string
  default     = "us-east-1"
  description = "default region in which to provision your AWS infra"
}

variable "aws_ssm_param_root_path" {
  type        = string
  description = "root to path under which SSM parameters created by this module will be created"
  default     = ""

  validation {
    condition     = length(var.aws_ssm_param_root_path) == 0 || length(regexall("/", var.aws_ssm_param_root_path)) == 0 || startswith(var.aws_ssm_param_root_path, "/")
    error_message = "The aws_ssm_param_root_path value must be fully qualified (begin with `/`) if it contains any `/` characters."
  }
}

variable "aws_ssm_key_id" {
  type        = string
  description = "KMS key id to use for encrypting SSM SecureString parameters created by this module, in any. (by default, will encrypt with AWS-managed keys)"
  default     = null
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

variable "gcp_project_id" {
  type        = string
  description = "id of GCP project that will host psoxy instance"
}

variable "environment_name" {
  type        = string
  description = "qualifier to append to name of project that will host your psoxy instance"
  default     = ""
}

variable "connector_display_name_suffix" {
  type        = string
  description = "suffix to append to display_names of connector SAs; helpful to distinguish between various ones in testing/dev scenarios"
  default     = ""
}

variable "psoxy_base_dir" {
  type        = string
  description = "the path where your psoxy repo resides"
  default     = null # will use `"${path.root}/.terraform/"` if not provided

  validation {
    condition     = var.psoxy_base_dir == null || can(regex(".*\\/$", var.psoxy_base_dir))
    error_message = "The psoxy_base_dir value should end with a slash."
  }
  validation {
    condition     = var.psoxy_base_dir == null || can(regex("^[^~].*$", var.psoxy_base_dir))
    error_message = "The psoxy_base_dir value should be absolute path (not start with ~)."
  }
}

variable "force_bundle" {
  type        = bool
  description = "whether to force build of deployment bundle, even if it already exists"
  default     = false
}

variable "general_environment_variables" {
  type        = map(string)
  description = "environment variables to add for all connectors"
  default     = {}
}


variable "enabled_connectors" {
  type        = list(string)
  description = "list of ids of connectors to enabled; see modules/worklytics-connector-specs"

  default = [
    "asana",
    "gdirectory",
    "gcal",
    "gmail",
    "gdrive",
    "google-chat",
    "google-meet",
    "hris",
    "slack-discovery-api",
    "zoom",
  ]
}

variable "non_production_connectors" {
  type        = list(string)
  description = "connector ids in this list will be in development mode (not for production use"
  default     = []
}

variable "custom_bulk_connectors" {
  type = map(object({
    source_kind = string
    rules = object({
      pseudonymFormat       = optional(string)
      columnsToRedact       = optional(list(string), [])
      columnsToInclude      = optional(list(string), [])
      columnsToPseudonymize = optional(list(string), [])
      columnsToDuplicate    = optional(map(string), {})
      columnsToRename       = optional(map(string), {})
    })
    settings_to_provide = optional(map(string), {})
  }))
  description = "specs of custom bulk connectors to create"

  default = {
    #    "custom-survey" = {
    #      source_kind = "survey"
    #      rules       = {
    #        columnsToRedact       = []
    #        columnsToPseudonymize = [
    #          "employee_id", # primary key
    #          # "employee_email", # if exists
    #        ]
    #      }
    #    }
  }
}

variable "lookup_table_builders" {
  type = map(object({
    input_connector_id            = string
    sanitized_accessor_role_names = list(string)
    rules = object({
      pseudonymFormat       = string
      columnsToRedact       = list(string)
      columnsToInclude      = list(string)
      columnsToPseudonymize = list(string)
      columnsToDuplicate    = map(string)
      columnsToRename       = map(string)
    })
  }))
  default = {
    #    "lookup-hris" = {
    #      input_connector_id = "hris",
    #      sanitized_accessor_role_names = [
    #        # ADD LIST OF NAMES OF YOUR AWS ROLES WHICH CAN READ LOOKUP TABLE
    #      ],
    #      rules       = {
    #        pseudonym_format = "URL_SAFE_TOKEN"
    #        columnsToRedact       = [
    #          "employee_email",
    #          "manager_id",
    #          "manager_email",
    #        ]
    #        columnsToPseudonymize = [
    #          "employee_id", # primary key
    #        ]
    #        columnsToDuplicate   = {
    #          "employee_id" = "employee_id_orig"
    #        }
    #        columnsToRename      = {}
    #        columnsToInclude     = null
    #      }
    #
    #    }
  }
}

variable "google_workspace_example_user" {
  type        = string
  description = "user to impersonate for Google Workspace API calls (null for none)"
}

variable "google_workspace_example_admin" {
  type        = string
  description = "user to impersonate for Google Workspace API calls (null for value of `google_workspace_example_user`)"
  default     = null # will failover to user
}
