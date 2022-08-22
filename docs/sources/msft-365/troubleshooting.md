# Microsoft 365 Troubleshooting


## Certificate creation via Terraform

Psoxy's terraform modules create certificates on your machine, and deploy these to Azure and the
keys to your AWS/GCP host environment. This all works via APIs.

Sometimes Azure is a bit finicky about certificate validity dates, and you get an error message
like this:

```
│ Error: Adding certificate for application with object ID "350c0b06-10d4-4908-8708-d5e549544bd0"
│
│   with module.msft-connection-auth["azure-ad"].azuread_application_certificate.certificate,
│   on ../../modules/azuread-local-cert/main.tf line 27, in resource "azuread_application_certificate" "certificate":
│   27: resource "azuread_application_certificate" "certificate" {
│
│ ApplicationsClient.BaseClient.Patch(): unexpected status 400 with OData
│ error: KeyCredentialsInvalidEndDate: Key credential end date is invalid.
╵
```

Just running `terraform apply` again (and maybe again) usually fixes it. Likely it's something with
with Azure's clock relative to your machine, plus whatever flight time is required between cert
generation and it being PUT to Azure.


