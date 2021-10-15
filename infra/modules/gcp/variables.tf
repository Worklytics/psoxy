variable "project_id" {
  type        = string
  description = "id of GCP project that will host psoxy instance"
}

variable "invoker_sa_emails" {
  type        = list(string)
  description = "emails of GCP service accounts to allow to invoke ALL cloud functions in target project"
}
