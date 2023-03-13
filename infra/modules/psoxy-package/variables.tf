
variable "path_to_psoxy_java" {
  type        = string
  description = "relative path from working directory (from which you call this module) to java/ folder within your checkout of the Psoxy repo"

  validation {
    condition     = fileexists(concat(var.path_to_psoxy_java, "pom.xml"))
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
  description = "version of psoxy to deploy"
  default     = "0.4.14"
}

variable "force_bundle" {
  type        = bool
  description = "whether to force build of deployment bundle, even if it already exists"
  default     = false
}
