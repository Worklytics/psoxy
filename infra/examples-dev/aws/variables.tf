variable "environment_name" {
  type        = string
  description = "qualifier to distinguish resources created by this terraform configuration from other psoxy Terraform deployments, (eg, 'prod', 'dev', etc)"
  default     = ""

  validation {
    condition     = !can(regex("^(?i)(aws|ssm)", var.environment_name))
    error_message = "The `environment_name` cannot start with 'aws' or 'ssm', as this will name your AWS resources with prefixes that displease the AMZN overlords."
  }
}

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
  description = "ARN of role Terraform should assume when provisioning your infra. (can be `null` if your CLI is auth'd as the right user/role)"
  default     = null
}

variable "aws_region" {
  type        = string
  description = "default region in which to provision your AWS infra"
  default     = "us-east-1"
}

variable "aws_ssm_param_root_path" {
  type        = string
  description = "root to path under which SSM parameters created by this module will be created; NOTE: shouldn't be necessary to use this is you're following recommended approach of using dedicated AWS account for deployment"
  default     = ""

  validation {
    condition     = length(var.aws_ssm_param_root_path) == 0 || length(regexall("/", var.aws_ssm_param_root_path)) == 0 || startswith(var.aws_ssm_param_root_path, "/")
    error_message = "The aws_ssm_param_root_path value must be fully qualified (begin with `/`) if it contains any `/` characters."
  }
}

variable "worklytics_host" {
  type        = string
  description = "host of worklytics instance where tenant resides. (e.g. intl.worklytics.co for prod; but may differ for dev/staging)"
  default     = "intl.worklytics.co"
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
      # see https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_iam-quotas.html
      # sources suggest limit of 64 chars for role names, but not clear if that includes paths so not checking it
      for i in var.caller_aws_arns : (length(regexall("^arn:aws:iam::\\d{12}:((role|user)\\/)?[A-Za-z0-9/=,.@_-]+$", i)) > 0)
    ])
    error_message = "The values of caller_aws_arns should be AWS Resource Names, something like 'arn:aws:iam::123123123123:root', 'arn:aws:iam::123123123123:user/ExampleUser', 'arn:aws:iam:123123123123:role/TestRole'" # for testing; can remove once prod-ready
  }
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

variable "gcp_project_id" {
  type        = string
  description = "string ID of GCP project that will host psoxy instance; must exist. Can leave null if not using GCP/Google Workspace."
  default     = null
}

variable "google_workspace_example_user" {
  type        = string
  description = "user to impersonate for Google Workspace API calls (null for none)"
  default     = null
}

variable "google_workspace_example_admin" {
  type        = string
  description = "user to impersonate for Google Workspace API calls (null for value of `google_workspace_example_user`)"
  default     = null # will failover to user
}

variable "salesforce_domain" {
  type        = string
  description = "Domain of the Salesforce to connect to (only required if using Salesforce connector). To find your My Domain URL, from Setup, in the Quick Find box, enter My Domain, and then select My Domain"
  default     = ""
}

variable "jira_server_url" {
  type        = string
  default     = null
  description = "(Only required if using Jira Server connector) URL of the Jira server (ex: myjiraserver.mycompany.com)"
}

variable "jira_cloud_id" {
  type        = string
  default     = null
  description = "(Only required if using Jira Cloud connector) Cloud id of the Jira Cloud to connect to (ex: 1324a887-45db-1bf4-1e99-ef0ff456d421)."
}

variable "example_jira_issue_id" {
  type        = string
  default     = null
  description = "(Only required if using Jira Server/Cloud connector) Id of an issue for only to be used as part of example calls for Jira (ex: ETV-12)"
}

variable "github_installation_id" {
  type        = string
  default     = null
  description = "(Only required if using Github connector) InstallationId of the application in your org for authentication with the proxy instance (ex: 123456)"
}

variable "github_organization" {
  type        = string
  default     = null
  description = "(Only required if using Github connector) Name of the organization to be used as part of example calls for Github (ex: Worklytics)"
}

variable "github_example_repository" {
  type        = string
  default     = null
  description = "(Only required if using Github connector) Name for the repository to be used as part of example calls for Github (ex: psoxy)"
}

variable "connector_display_name_suffix" {
  type        = string
  description = "suffix to append to display_names of connector SAs; helpful to distinguish between various ones in testing/dev scenarios"
  default     = ""
}

variable "psoxy_base_dir" {
  type        = string
  description = "the path where your psoxy repo resides. Preferably a full path, /home/user/repos/, avoid tilde (~) shortcut to $HOME"

  validation {
    condition     = can(regex(".*\\/$", var.psoxy_base_dir))
    error_message = "The psoxy_base_dir value should end with a slash."
  }
}

variable "force_bundle" {
  type        = bool
  description = "whether to force build of deployment bundle, even if it already exists for this proxy version"
  default     = false
}

variable "provision_testing_infra" {
  type        = bool
  description = "whether to provision infra needed to support testing of deployment"
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

variable "enabled_connectors" {
  type        = list(string)
  description = "list of ids of connectors to enabled; see modules/worklytics-connector-specs"
}

variable "non_production_connectors" {
  type        = list(string)
  description = "connector ids in this list will be in development mode (not for production use)"
  default     = []
}

variable "bulk_input_expiration_days" {
  type        = number
  description = "Number of days after which objects in the bucket will expire. This could be as low as 1 day; longer aids debugging of issues."
  default     = 30
}

variable "bulk_sanitized_expiration_days" {
  type        = number
  description = "Number of days after which objects in the bucket will expire. In practice, Worklytics syncs data ~weekly, so 30 day minimum for this value."
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
      pseudonymFormat       = optional(string, "URL_SAFE_TOKEN")
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

# TODO: rethink this schema before we publish this
variable "lookup_table_builders" {
  type = map(object({
    input_connector_id            = string
    sanitized_accessor_role_names = list(string)
    rules = object({
      pseudonymFormat       = optional(string, "URL_SAFE_TOKEN")
      columnsToRedact       = optional(list(string))
      columnsToInclude      = optional(list(string))
      columnsToPseudonymize = optional(list(string))
      columnsToDuplicate    = optional(map(string))
      columnsToRename       = optional(map(string))
    })
  }))
  default = {
    #    "hris-lookup" = {
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

variable "todos_as_outputs" {
  type        = bool
  description = "whether to render TODOs as outputs or flat files (former useful if you're using Terraform Cloud/Enterprise, or somewhere else where the filesystem is not readily accessible to you)"
  default     = false
}

variable "user_principal_email" {
  type        = string
  description = "The email of the user that has been granted access to the Worklytics Tenant API (configure in Worklytics Web App)."
}
