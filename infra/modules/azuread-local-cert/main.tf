# generate certificate for Azure AD application locally and deploy it to Azure AD
# NOTE: certificate will only be temporarily written to your local file system, but out of abundance
# of caution should:
#  - run this only in an environment that is approved from key generation in your organization
#  - use a secure location for your Terraform state (eg, not local file systme of your laptop)
terraform {
  required_providers {
    azuread = {
      version = ">= 2.44, < 4.0"
    }
  }
}

resource "time_rotating" "rotation" {
  rotation_days = var.rotation_days
}

# done with external as Terraform docs suggest

data "external" "certificate" {
  program = ["${path.module}/local-cert.sh", var.certificate_subject, var.cert_expiration_days]
}

# for JWT signing
# NOTE: have gotten '400 with OData error: KeyCredentialsInvalidEndDate: Key credential end date is invalid'
# when trying to apply this, even though only using 6 month expiration window. Re-apply worked ...
resource "azuread_application_certificate" "certificate" {
  application_id = var.application_id
  type           = "AsymmetricX509Cert"
  value          = base64decode(data.external.certificate.result.cert)
  end_date       = timeadd(time_rotating.rotation.id, "${var.cert_expiration_days * 24}h")

  lifecycle {
    create_before_destroy = true
  }
}

output "private_key_id" {
  # hackery to translate output of openssl fingerprint --> string of hex chars equivalent to how MSFT does
  # (eg, MSFT will compute fingerprint server-side of the certificate value posted above)
  # "SHA1 Fingerprint=8F:0E:46:89:7A:41:AE:5E:93:8C:BA:56:FC:AA:49:6E:0F:E2:F4:2C" -->
  #   "8F0E46897A41AE5E938CBA56FCAA496E0FE2F42C"
  value = replace(replace(data.external.certificate.result.fingerprint, "SHA1 Fingerprint=", ""), ":", "")
}

output "private_key" {
  value     = base64decode(data.external.certificate.result.key_pkcs8)
  sensitive = true
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

