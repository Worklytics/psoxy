variable "s3_path" {
  type        = string
  description = "The S3 path to which statement will allow reads and writes`bucket-name/path/`."
  default     = null

  validation {
    condition     = var.s3_path == null || can(regex("^[a-zA-Z][a-zA-Z0-9-_ ]*[a-zA-Z0-9](/.*)?$", var.s3_path))
    error_message = "The `s3_path` must be a valid S3 bucket address. May include a path within the bucket, in which case we highly recommend you end it with a slash (`/`)."
  }
}
