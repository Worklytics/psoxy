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
      for i in var.caller_aws_arns : (length(regexall("^arn:aws:iam::\\d{12}:((role|user)\\/)?\\w+$", i)) > 0)
    ])
    error_message = "The values of caller_aws_arns should be AWS Resource Names, something like 'arn:aws:iam::914358739851:root'."
  }
}

variable "environment_name" {
  type        = string
  description = "qualifier to distinguish resources created by this terraform configuration from other psoxy Terraform deployments, (eg, 'prod', 'dev', etc)"
  default     = "psoxy"

  validation {
    condition     = !can(regex("^(?i)(aws|ssm)", var.environment_name))
    error_message = "The `environment_name` cannot start with 'aws' or 'ssm', as this will name your AWS resources with prefixes that displease the AMZN overlords."
  }
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

variable "provision_testing_infra" {
  type        = bool
  description = "Whether to provision infra needed to support testing of deployment. If false, it's left to you to ensure the AWS principal you use when running test scripts has the correct permissions."
  default     = false
}

variable "install_test_tool" {
  type        = bool
  description = "whether to install the test tool (can be 'false' if Terraform not running from a machine where you intend to run tests of your Psoxy deployment)"
  default     = true
}

variable "pseudonymize_app_ids" {
  type        = string
  description = "if set, will set value of PSEUDONYMIZE_APP_IDS environment variable to this value for all sources"
  default     = true
}

variable "general_environment_variables" {
  type        = map(string)
  description = "environment variables to add for all connectors"
  default     = {}
}

variable "api_connectors" {
  type = map(object({
    source_kind                           = string
    source_auth_strategy                  = string
    target_host                           = string
    oauth_scopes_needed                   = optional(list(string), [])
    environment_variables                 = optional(map(string), {})
    example_api_calls                     = optional(list(string), [])
    example_api_calls_user_to_impersonate = optional(string)
    secured_variables = optional(list(object({
      name     = string
      value    = optional(string)
      writable = optional(bool, false)
    })),
      [])

  }))

  description = "map of API connectors to provision"
}

variable "non_production_connectors" {
  type        = list(string)
  description = "connector ids in this list will be in development mode (not for production use"
  default     = []
}

variable "custom_api_connector_rules" {
  type        = map(string)
  description = "map of connector id --> YAML file with custom rules"
  default     = {}
}

variable "bulk_connectors" {
  type = map(object({
    source_kind = string
    rules = object({
      pseudonymFormat       = optional(string)
      columnsToRedact       = optional(list(string), [])
      columnsToInclude      = optional(list(string), null)
      columnsToPseudonymize = optional(list(string), [])
      columnsToDuplicate    = optional(map(string), {})
      columnsToRename       = optional(map(string), {})
    })
    example_file        = optional(string)
    settings_to_provide = optional(map(string), {})
  }))

  description = "map of connector id  => bulk connectors to provision"
}

variable "bulk_input_expiration_days" {
  type        = number
  description = "**alpha** Number of days after which objects in the bucket will expire"
  default     = 30
}

variable "bulk_sanitized_expiration_days" {
  type        = number
  description = "**alpha** Number of days after which objects in the bucket will expire"
  default     = 720
}

variable "lookup_table_builders" {
  type = map(object({
    input_connector_id            = string
    sanitized_accessor_role_names = list(string)
    rules = object({
      pseudonymFormat       = optional(string)
      columnsToRedact       = optional(list(string))
      columnsToInclude      = optional(list(string))
      columnsToPseudonymize = optional(list(string))
      columnsToDuplicate    = optional(map(string))
      columnsToRename       = optional(map(string))
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

variable "todo_step" {
  type        = number
  description = "of all todos, where does this one logically fall in sequence"
  default     = 2
}
