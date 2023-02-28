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


If security risks of managing a certificate with Terraform are not acceptable, we suggest:
  1. generate the certificate(s) outside of terraform by invoking `local-cert.sh` script directly
  2. pass the `fingerprint` value(s) from the resulting JSON as a variable to your Terraform
     configuration, so that the "private key id" SSM param can be correctly populated
  3. take the `key` value(s) from the resulting JSON, and directly set the value of the SSM parameter
     via AWS console or the cli.  This could be done by a user with write-only permission to the
     parameter.

Prereqs:
  - `openssl` We have not documented exact version required, so YMMV.
  - [`jq`](https://stedolan.github.io/jq/)

Example:
```shell

# change the subject to something more appropriate for your organization; use TTL in days that you like
./local-cert.sh "/C=US/ST=New York/L=New York/CN=www.worklytics.co" 180 > cert.json

cat cert.json | jq -r .fingerprint

# take the hex value, without the ':' characters as the value to pass to your terraform config
# (or directly fill relevant PRIVATE_KEY_ID value in secret manager of your target cloud)

export KEY_PKCS8=`cat cert.json | jq -r .key_pkcs8 | base64 --decode`

# gives you `KEY_PKCS8` env variable, which you could then use to fill secret in secret manager of your choice

cat cert.json | jq -r .key | base64 --decode > cert.pem

# gives you a certificate you can upload directly to Azure AD console
# the value you see for 'Thumbprint' in the Azure AD console should MATCH the value you set for
# Private Key ID in the secret manager of your target cloud


# remember to clean up the files into which you just wrote your certificate/keys!!
rm cert.pem
rm cert.json
```

see:
  - https://docs.aws.amazon.com/cli/latest/reference/ssm/put-parameter.html
  - https://cloud.google.com/sdk/gcloud/reference/secrets/versions/add

## Non-Terraform Alternative, No Shell Script

Don't want to clone our whole repo, just to use our `local-cert.sh` script? Then you can use all of
the following commands directly from your shell.

Prereqs:
  - `openssl` We have not documented exact version required, so YMMV.
  - [`jq`](https://stedolan.github.io/jq/)

```shell
SUBJECT="/C=US/ST=New York/L=New York/CN=www.worklytics.co"
openssl req -x509 -newkey rsa:2048 -subj $SUBJECT -keyout key.pem -out cert.pem -days 180 -nodes
openssl pkcs8 -nocrypt -in key.pem -inform PEM -topk8 -outform PEM -out key_pkcs8.pem

openssl x509 -in cert.pem -noout -fingerprint
# take the hex value, without the ':' characters as the value to pass to your terraform config
# (or directly fill relevant PRIVATE_KEY_ID value in secret manager of your target cloud)

# 1. upload the cert.pem to the app in your Azure AD console (the value it then shows for 'Thumbprint' should match the value you set for Private Key ID in the secret manager of your target cloud)
# 2. set the *content* of key_pkcs8.pem as the value of the secret for your proxy's private key

# clean everything up!!
rm key.pem
rm cert.pem
rm key_pkcs8.pem
```
