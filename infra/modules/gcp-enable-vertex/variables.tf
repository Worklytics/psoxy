variable "project_id" {
  type        = string
  description = "GCP project in which to enable Vertex AI and grant invoke access"
}

variable "service_accounts" {
  type        = map(string)
  description = "Map of stable connector id → service account email that may invoke Vertex AI (`roles/aiplatform.user`). Keys must be known at plan time; do not use computed emails as keys."
  default     = {}
}
