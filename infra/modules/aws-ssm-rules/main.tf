# custom REST rules as SSM parameter

# as a module to get reusable
# - variable validation
# - length calculations

# pros:
#  - if small, human readable.  if large, can be gzipped
#  - can be reviewed/managed via AWS console
#  - rule changes readily visible in plan, not confused with source code changes
#  - bundle the same for all lambdas; speeds build/package, reduces disk/mem footprint needed for
#    deploy (concern if doing cloud shell)
#
# cons vs shipping with lambda's deployment as flat file:
#  - lambda won't see rule changes unless restarted
#  - another component to see/manage
#  - less analogous to GCP; haven't implemented gcp yet, but we're using secret manager
#  - need to worry about lambda's permissions to read the SSM param
#  - need to worry about terraforms permissions to write the SSM param (if we're not otherwise
#    writing SSMs, which as of Apr 2023 we are so not really concern)

# compress if necessary; but otherwise leave plain so human readable
locals {
  rules_plain      = file(var.file_path)
  rules_compressed = base64gzip(local.rules_plain)
  use_compressed   = length(local.rules_plain) > 8192
  param_value      = local.use_compressed ? local.rules_compressed : local.rules_plain
}

resource "aws_ssm_parameter" "rules" {
  name           = "${var.prefix}RULES"
  type           = "String"
  tier           = length(local.param_value) < 4096 ? "Standard" : "Advanced"
  insecure_value = local.param_value
}

output "rules_hash" {
  value = sha1(local.rules_plain)
}
