resource "time_rotating" "rotation" {
  rotation_days = var.rotation_days
}

# done with external as Terraform docs suggest
# but still out of abundance of caution should:
#  - run this only in an environment that is approved from key generation in your organization
#  - use a secure location for your Terraform state (eg, not local file systme of your laptop)
data "external" "certificate" {
  working_dir = path.module
  program     = ["local-cert.sh", var.certificate_subject, var.cert_expiration_days]
}

resource "random_uuid" "key-id" {

}

# for JWT signing
resource "azuread_application_certificate" "certificate" {
  application_object_id = var.application_id
  type                  = "AsymmetricX509Cert"
  value                 = base64decode(data.external.certificate.result.cert)
  end_date              = timeadd(time_rotating.rotation, var.cert_expiration_days)
}

output "private_key_id" {
  value = base64sha256(data.external.certificate.result.cert)
}

output "private_key" {
  value = data.external.certificate.result.cert
}

# for 3-legged OAuth flows, which believe aren't needed in this case as we have no OIDC/sign-on
# flow for psoxy use-cases
#resource "azuread_application_password" "oauth-client-secret" {
#  application_object_id = var.application_id # oauthClientId
#
#  rotate_when_changed = {
#    rotation = time_rotating.rotation.id
#  }
#}

#output "oauth_client_secret" {
#  value = azuread_application_password.oauth-client-secret.value
#}

