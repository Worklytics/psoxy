variable "project_id" {
  type        = string
  description = "name of the gcp project"
}

variable "secret_id" {
  type        = string
  description = "id of the secret"
}

variable "user_emails" {
  type        =  list(string)
  description = "email addresses, if any, of a GCP (Google) users who will be granted short-lived permissions to add a version of the secret"
  default     = []
}

variable "grant_duration" {
  type        =  string
  default     = "24h"
  description = "how long grant of 'secretVersionAdder' role should be valid for (default '24h'); see https://www.terraform.io/docs/language/functions/timeadd.html"
}
