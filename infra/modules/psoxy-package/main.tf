# packages psoxy for deployment

# TODO: solve weirdness with AWS case: AWS lambda is deployed directly by Terraform from a code
# package built by terraform, but terraform doesn't understand this dependency in its plan. we make
# it work via explicit 'depends_on', but Terraform still gives 'error inconsistent plan' as its
# original plan presumed the sha-256 of the package
locals {
  path_to_core_module    = "${var.path_to_psoxy_java}/core"
  path_to_impl_module    = "${var.path_to_psoxy_java}/impl/${var.implementation}"
  path_to_deployment_jar = "${local.path_to_impl_module}/target/psoxy-${var.implementation}-1.0-SNAPSHOT.jar"
}

resource "null_resource" "core_package" {
  triggers = {
    pom_hash = filebase64sha256("${local.path_to_core_module}/pom.xml")
  }

  provisioner "local-exec" {
    working_dir = local.path_to_core_module
    command     = "mvn package install"
  }
}

resource "null_resource" "deployment_package" {
  triggers = {
    pom_hash = filebase64sha256("${local.path_to_impl_module}/pom.xml")
  }

  provisioner "local-exec" {
    working_dir = local.path_to_impl_module
    command     = "mvn package"
  }

  # can't build implementation package to deploy until core pkg is built and installed to local mvn repo
  depends_on = [
    null_resource.core_package
  ]
}


output "deployment_package_hash" {
  value = filebase64sha256(local.path_to_deployment_jar)

  depends_on = [
    null_resource.deployment_package
  ]
}

output "path_to_deployment_jar" {
  value = local.path_to_deployment_jar
}
