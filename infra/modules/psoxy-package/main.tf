# packages psoxy for deployment

# TODO: solve weirdness with AWS case: AWS lambda is deployed directly by Terraform from a code
# package built by terraform, but terraform doesn't understand this dependency in its plan. we make
# it work via explicit 'depends_on', but Terraform still gives 'error inconsistent plan' as its
# original plan presumed the sha-256 of the package
locals {
  filename               = "psoxy-${var.implementation}-${var.psoxy_version}-SNAPSHOT.jar"
  path_to_impl_module    = "${var.path_to_psoxy_java}/impl/${var.implementation}"
  path_to_deployment_jar = "${local.path_to_impl_module}/target/${local.filename}"
}


# build psoxy bundle with external build script, via a 'data' resource.
# NOTE: since invoked via 'data', happens during plan, rather than 'apply'; this is incorrect
# semantics if you view the package as a 'resource', but is reasonable if you view it as just a
# configuration value (eg, the content of a lambda function)
# NOTE: pass deployment jar path through the script simply to make outputs of this Terraform module
# dependent on the build script actually running, avoiding Terraform thinking the outputs are
# known prior to executing the build

data "external" "deployment_package" {
  program = ["${path.module}/build.sh", var.path_to_psoxy_java, var.implementation, local.path_to_deployment_jar]
}


output "deployment_package_hash" {
  value = filebase64sha256(data.external.deployment_package.result.path_to_deployment_jar)
}

output "path_to_deployment_jar" {
  value = data.external.deployment_package.result.path_to_deployment_jar
}

output "filename" {
  value = local.filename
}
