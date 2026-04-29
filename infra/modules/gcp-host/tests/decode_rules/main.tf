variable "encoded" {
  type        = string
  description = "The base64-gzipped encoded YAML string"
}

data "external" "decoder" {
  program = ["bash", "-c", "printf \"%s\" \"$1\" | base64 -d | gzip -d | jq -R -s '{yaml: .}'", "--", var.encoded]
}

output "decoded" {
  value = yamldecode(data.external.decoder.result.yaml)
}
