# Azure AD Local Certificate **deprecated**

**DEPRECATED** - will be removed in v0.5; this is not recommended approach, for a variety of
reasons, since Microsoft release support for federated credentials in ~Sept 202. See our module
`azuread-federated-credentials` for preferred alternative.

Module to generate a certificate locally, using `openssl`, and push it to target Azure AD application.

Prereqs:
  - auth'd in Azure CLI as user who can update certificate on Azure AD enterprise application listing
  - `openssl` We have not documented exact version required, so YMMV.
  - [`jq`](https://stedolan.github.io/jq/)

NOTE:
  - the key used for the certificate WILL be stored by Terraform in your local state. You should
    1) run this from a secure location, 2) store your Terraform state securely
  - this will regenerate certificates on every run. In simple scenarios, this is probably desirable,
    but may be a nuisance if it's part of a large Terraform configuration.


## Non-Terraform Alternative


If security risks of managing a certificate with Terraform are not acceptable, you can generate
the certificate(s) manually using `local-cert-standalone.sh` script directly and following the
instructions it generates.

Prereqs:
  - `openssl` We have not documented exact version required, so YMMV.

Example:
```shell
# USAGE: ./local-cert-standalone.sh subject ttl tool
# tool is just used to generate distinct file instructions per tool

# change the subject to something more appropriate for your organization; use TTL in days that you like
./local-cert-standalone.sh "/C=US/ST=New York/L=New York/CN=www.worklytics.co" 180 outlook-calendar
```

Output will be something similar to this:
```shell
Open TODO_OUTLOOK-CAL_CERTS.md and follow the instructions to complete the setup.
```

The bash script generates a markdown [file similar to this example](TODO_OUTLOOK-CAL_CERTS_SAMPLE.md).

For updating values in the secret manager see:
  - https://docs.aws.amazon.com/cli/latest/reference/ssm/put-parameter.html
  - https://cloud.google.com/sdk/gcloud/reference/secrets/versions/add
