variable "emails" {
  type = list(string)
  description = "list of object ids to be set as owner of the application"
  default = []
}