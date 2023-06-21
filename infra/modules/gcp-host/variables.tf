variable "gcp_project_id" {
  type        = string
  description = "id of GCP project that will host psoxy instance"
}

variable "environment_name" {
  type        = string
  description = "Qualifier to append to names/ids of resources for psoxy. If not empty, A-Za-z0-9 or - characters only. Max length 10. Useful to distinguish between deployments into same GCP project."
  default     = "psoxy"

  validation {
    condition     = can(regex("^[A-z0-9\\-]{0,20}$", var.environment_name))
    error_message = "The environment_name must be 0-20 chars of [A-z0-9\\-] only."
  }
}

variable "config_parameter_prefix" {
  type        = string
  description = "A prefix to give to all config parameters (GCP Secret Manager Secrets) created/consumed by this module. If omitted, and `environment_id` provided, that will be used."
  default     = ""

  # taken from https://cloud.google.com/secret-manager/docs/reference/rpc/google.cloud.secrets.v1beta1
  # secret IDs can be up to 255 chars, so limit prefix to 120 gives plenty of leeway
  validation {
    condition     = can(regex("^[A-z0-9\\-_]{0,120}$", var.config_parameter_prefix))
    error_message = "The config_parameter_prefix must be 0-120 chars of [A-z0-9\\-_] only."
  }
}

variable "worklytics_sa_emails" {
  type        = list(string)
  description = "service accounts for your organization's Worklytics instances (list supported for test/dev scenarios)"
}

variable "psoxy_base_dir" {
  type        = string
  description = "the path where your psoxy repo resides"

  validation {
    condition     = can(regex(".*\\/$", var.psoxy_base_dir))
    error_message = "The psoxy_base_dir value should end with a slash."
  }
  validation {
    condition     = can(regex("^[^~].*$", var.psoxy_base_dir))
    error_message = "The psoxy_base_dir value should be absolute path (not start with ~)."
  }
}

variable "deployment_bundle" {
  type        = string
  description = "path to deployment bundle to use (if not provided, will build one)"
  default     = null
}

variable "force_bundle" {
  type        = bool
  description = "whether to force build of deployment bundle, even if it already exists"
  default     = false
}

variable "install_test_tool" {
  type        = bool
  description = "whether to install the test tool (can be 'false' if Terraform not running from a machine where you intend to run tests of your Psoxy deployment)"
  default     = true
}

variable "general_environment_variables" {
  type        = map(string)
  description = "environment variables to add for all connectors"
  default     = {}
}

variable "pseudonymize_app_ids" {
  type        = string
  description = "if set, will set value of PSEUDONYMIZE_APP_IDS environment variable to this value for all sources"
  default     = true
}

variable "gcp_region" {
  type        = string
  description = "Region in which to provision GCP resources, if applicable"
  default     = "us-central1"
}

variable "replica_regions" {
  type        = list(string)
  description = "List of regions in which to replicate secrets."
  default = [
    "us-central1",
    "us-west1",
  ]
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
      lockable = optional(bool, false)
      })),
    [])

  }))

  description = "map of API connectors to provision"
}

# q: better to flatten this into connectors themselves?
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

variable "non_production_connectors" {
  type        = list(string)
  description = "connector ids in this list will be in development mode (not for production use"
  default     = []
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

variable "custom_bulk_connector_rules" {
  type        = map(object({
    pseudonymFormat       = optional(string, "URL_SAFE_TOKEN")
    columnsToRedact       = optional(list(string))
    columnsToInclude      = optional(list(string))
    columnsToPseudonymize = optional(list(string))
    columnsToDuplicate    = optional(map(string))
    columnsToRename       = optional(map(string))
  }))

  description = "map of connector id --> rules object"
  default     = {}
}


# build lookup tables to JOIN data you receive back from Worklytics with your original data.
#   - `join_key_column` should be the column you expect to JOIN on, usually 'employee_id'
#   - `columns_to_include` is an optional a list of columns to include in the lookup table,
#                       e.g. if the data you're exporting TO worklytics contains more columns than
#                       you want to have in the lookup table, you can limit to an explicit list
#   - `sanitized_accessor_names` is an optional list of GCP principals, by email with qualifier, eg:
#                       `user:alice@worklytics`, `group:analysts@worklytics.co`, or
#                        `serviceAccount:sa@worklytics.google-service-accounts.com`
variable "lookup_tables" {
  type = map(object({
    source_connector_id           = string
    join_key_column               = string
    columns_to_include            = optional(list(string))
    sanitized_accessor_principals = optional(list(string))
    expiration_days               = optional(number, 5 * 365)
  }))
  description = "Lookup tables to build from same source input as another connector, output to a distinct bucket. The original `join_key_column` will be preserved, "

  default = {
    #  "lookup-hris" = {
    #      source_connector_id = "hris",
    #      join_key_column = "employee_id",
    #      columns_to_include = null
    #      sanitized_accessor_principals = [
    #        # ADD LIST OF GCP PRINCIPALS HERE
    #      ],
    #  }
  }
}

variable "todo_step" {
  type        = number
  description = "of all todos, where does this one logically fall in sequence"
  default     = 2
}
