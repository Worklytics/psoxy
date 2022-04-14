# Azure AD Local Certificate

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

Example:
```shell

# change the subject to something more appropriate for your organization; use TTL in days that you like
./local-cert.sh "/C=US/ST=New York/L=New York/CN=www.worklytics.co" 180 > cert.json

cat cert.json | jq -r .fingerprint

# take the hex value, without an ':' characters as the value to pass to your terraform config
# (or directly fill relevant CLIENT_ID value in secret manager of your target cloud)

export KEY_PKCS8=`cat cert.json | jq -r .key_pkcs8 | base64 --decode`

# gives you `KEY_PKCS8` env variable, which you could then use to fill secret in secret manager of your choice

cat cert.json | jq -r .key | base64 --decode > cert.pem

# gives you a certificate you can upload directly to Azure AD console

# remember to clean up the files into which you just wrote your certificate/keys!!
rm cert.pem
rm cert.json
```

see:
  - https://docs.aws.amazon.com/cli/latest/reference/ssm/put-parameter.html
  - https://cloud.google.com/sdk/gcloud/reference/secrets/versions/add

