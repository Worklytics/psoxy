
variable "path_to_psoxy_java" {
  type        = string
  description = "relative path from working directory (from which you call this module) to java/ folder within your checkout of the Psoxy repo"

  validation {
    condition     = fileexists(format("%s/pom.xml", var.path_to_psoxy_java))
    error_message = "The path_to_psoxy_java value should be a path to a directory that exists, containing java/ folder with Psoxy code to compile."
  }
}

variable "implementation" {
  type        = string
  description = "reference to implementation to build (subdirectory of java/impl/)"
  default     = "aws"
}

variable "psoxy_version" {
  type        = string
  description = "IGNORED; version of psoxy to deploy"
  default     = null
}

variable "deployment_bundle" {
  type        = string
  description = "path to deployment bundle to use (if not provided, will build one)"
  default     = null
}

variable "skip_tests" {
  type        = bool
  description = "whether to skip tests when building the deployment bundle"
  default     = true
}

variable "force_bundle" {
  type        = bool
  description = "whether to force build of deployment bundle, even if it already exists"
  default     = false
}
