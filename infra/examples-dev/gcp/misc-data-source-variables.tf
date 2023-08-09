# Terraform variables for miscellaneous data sources
# (not coupled to hosting environment; so split into separate file to ease keeping them in sync
#  across AWS/GCP examples.  DRY!!)

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

variable "jira_example_issue_id" {
  type        = string
  default     = null
  description = "If using Jira Server/Cloud connector, provide id of an issue for only to be used as part of example calls for Jira (ex: ETV-12)"
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

variable "salesforce_example_account_id" {
  type        = string
  default     = null
  description = "(Only required if using Salesforce connector) Id of the account id for usign as an example calls for Salesforce (ex: 0015Y00002c7g95QAA)"
}

locals {
  # tflint-ignore: terraform_unused_declarations
  validate_salesforce_domain         = (var.salesforce_domain == null || var.salesforce_domain == "" || can(regex(":|\\/", try(var.salesforce_domain, "")))) && contains(var.enabled_connectors, "salesforce")
  validate_salesforce_domain_message = "The salesforce_domain var should be populated and to be with only the domain without protocol or query paths if enabled."
  validate_salesforce_domain_check = regex(
    "^${local.validate_salesforce_domain_message}$",
    (!local.validate_salesforce_domain
      ? local.validate_salesforce_domain_message
  : ""))
}