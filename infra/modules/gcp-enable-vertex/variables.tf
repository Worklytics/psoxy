variable "project_id" {
  type        = string
  description = "GCP project in which to enable Vertex AI and grant invoke access"
}

variable "service_account_emails" {
  type        = set(string)
  description = "Service accounts that may invoke Vertex AI (`roles/aiplatform.user`)"
  default     = []
}
