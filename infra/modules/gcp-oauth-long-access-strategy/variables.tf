variable "project_id" {
  type        = string
  description = "name of the gcp project"
}

variable "function_name" {
  type        = string
  description = "name of cloud function"
}

variable "token_adder_user_emails" {
  type        =  list(string)
  description = "email addresses, if any of a GCP (Google) user who will be granted short-lived permissions to populate the token value"
  default     = []
}

variable "token_adder_grant_duration" {
  type        =  string
  default     = "24h"
  description = "how long grant of 'secretVersionAdder' role should be valid for (default '24h'); see https://www.terraform.io/docs/language/functions/timeadd.html"
}
