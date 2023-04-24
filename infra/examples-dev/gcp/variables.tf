variable "gcp_project_id" {
  type        = string
  description = "id of GCP project that will host psoxy instance"
}

variable "gcp_terraform_sa_account_email" {
  type        = string
  description = "Email of GCP service account that will be used to provision GCP resources. Leave 'null' to use application default for you environment."
  default     = null

  validation {
    condition     = var.gcp_terraform_sa_account_email == null || can(regex(".*@.*\\.iam\\.gserviceaccount\\.com$", var.gcp_terraform_sa_account_email))
    error_message = "The gcp_terraform_sa_account_email value should be a valid GCP service account email address."
  }
}

variable "environment_id" {
  type        = string
  description = "Qualifier to append to names/ids of resources for psoxy. If not empty, A-Za-z0-9 or - characters only. Max length 10. Useful to distinguish between deployments into same GCP project."
  default     = ""

  validation {
    condition     = can(regex("^[A-z0-9\\-]{0,20}$", var.environment_id))
    error_message = "The environment_id must be 0-20 chars of [A-z0-9\\-] only."
  }
}

variable "config_parameter_prefix" {
  type        = string
  description = "A prefix to give to all config parameters (GCP Secret Manager Secrets) created/consumed by this module. If omitted, and `environment_id` provided, that will be used."
  default     = ""
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

variable "enabled_connectors" {
  type        = list(string)
  description = "list of ids of connectors to enabled; see modules/worklytics-connector-specs"
}

variable "non_production_connectors" {
  type        = list(string)
  description = "connector ids in this list will be in development mode (not for production use"
  default     = []
}

variable "bulk_input_expiration_days" {
  type        = number
  description = "Number of days after which objects in the bucket will expire"
  default     = 30
}

variable "bulk_sanitized_expiration_days" {
  type        = number
  description = "Number of days after which objects in the bucket will expire"
  default     = 1805 # 5 years; intent is 'forever', but some upperbound in case bucket is forgotten
}

variable "custom_rest_rules" {
  type        = map(string)
  description = "map of connector id --> YAML file with custom rules"
  default     = {}
}

variable "custom_bulk_connectors" {
  type = map(object({
    source_kind = string
    rules = object({
      pseudonymFormat       = optional(string)
      columnsToRedact       = optional(list(string))
      columnsToInclude      = optional(list(string))
      columnsToPseudonymize = optional(list(string))
      columnsToDuplicate    = optional(map(string))
      columnsToRename       = optional(map(string))
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

variable "google_workspace_example_user" {
  type        = string
  description = "User to impersonate for Google Workspace API calls (null for none)"
}

variable "google_workspace_example_admin" {
  type        = string
  description = "user to impersonate for Google Workspace API calls (null for value of `google_workspace_example_user`)"
  default     = null # will failover to user
}

variable "salesforce_domain" {
  type        = string
  default     = ""
  description = "Domain of the Salesforce to connect to (only required if using Salesforce connector). To find your My Domain URL, from Setup, in the Quick Find box, enter My Domain, and then select My Domain"
}

variable "msft_tenant_id" {
  type        = string
  description = "ID of Microsoft tenant to connect to (req'd only if config includes MSFT connectors)"
  default     = ""
}

variable "msft_owners_email" {
  type        = set(string)
  description = "(Only if config includes MSFT connectors). Optionally, set of emails to apply as owners on AAD apps apart from current logged user"
  default     = []
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
    expiration_days               = optional(number)
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

variable "todos_as_outputs" {
  type        = bool
  description = "whether to render TODOs as outputs or flat files (former useful if you're using Terraform Cloud/Enterprise, or somewhere else where the filesystem is not readily accessible to you)"
  default     = false
}
