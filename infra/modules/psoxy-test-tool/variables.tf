variable "path_to_tools" {
  type        = string
  description = "relative path from working directory (from which you call this module) to java/ folder within your checkout of the Psoxy repo"
}

variable "psoxy_version" {
  type = string
  description = "version of psoxy"
}
