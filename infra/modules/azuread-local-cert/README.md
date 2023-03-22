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
Open TODO_OUTLOOK-CAL_CERTS.txt and follow the instructions to complete the setup.
```

File will show instructions like:
```shell
Follow instructions and copy the contents between the === blocks
IMPORTANT: After setup complete please remove this file.


TODO 1: Upload the following cert to the outlook-cal app in your Azure Console
Or give it to an admin with rights to do so.
========================================================================
-----BEGIN CERTIFICATE-----
MIIDfzCCAmegAwIBAgIUPvjrREWDpCA+lGBgMlgXnAFFhv4wDQYJKoZIhvcNAQEL
BQAwTzELMAkGA1UEBhMCVVMxETAPBgNVBAgMCE5ldyBZb3JrMREwDwYDVQQHDAhO
....
5iyo1IlTn58CEEhpRLE0PX41gk3JZQcYHnRKstm7mHjablhvFXIQYesyUkSxWW/4
QqM5kxgLumxKsZ/uZ7WJDCRj797nTWhCniuyZY/seNA+GrF+I3zU6eLv0oKq1OOI
zb7K1KKpaJbyijMF5/kxkPyUU0+D3DOIApBOowd0T1Rk0dc=
-----END CERTIFICATE-----
========================================================================

TODO 2: Update the value of PSOXY_OUTLOOK-CAL_PRIVATE_KEY_ID with the content between the starred blocks
========================================================================
A61EE02A71D2FB50EA9B783A8C4F351DE577D802
========================================================================

TODO 3: Update the value of PSOXY_OUTLOOK-CAL_PRIVATE_KEY with the content between the starred blocks
========================================================================
-----BEGIN PRIVATE KEY-----
MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQDTY8emar/W75Pq
R5xYn/zYviZHa8R1fmDSWdvAOvybfprb76jHfbK82J+uBXHBCtzRoqbOPljFj1n5
......
DLy7Z9eiKTRCkmzUYoBvZVueSKlknBWwKcf3p8pB+teXzdLXMP1xEtmLf9j06I4T
Sr/anUFqP5UPW742GSX74g==
-----END PRIVATE KEY-----
========================================================================

```

see:
  - https://docs.aws.amazon.com/cli/latest/reference/ssm/put-parameter.html
  - https://cloud.google.com/sdk/gcloud/reference/secrets/versions/add
