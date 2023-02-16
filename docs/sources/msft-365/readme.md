# Microsoft 365 

## Required roles
Examples `msft-365` will create an *Azure Active Directory* (AAD) application if there is any MSFT connector enabled, such 
as `azure-ad`, `outlook-cal` or `outlook-mail`. The AAD application created will have the right permissions to access to
MSFT Graph API to perform the requests from proxy.

For this, the user who run the Terraform code should have [Application Administrator](https://learn.microsoft.com/en-us/azure/active-directory/roles/permissions-reference#application-administrator)
role enabled; otherwise applications cannot be properly managed.

### Admin consent
Once all AAD applications are created it is required to perform the [admin consent](https://learn.microsoft.com/en-us/azure/active-directory/manage-apps/grant-admin-consent?pivots=ms-graph#prerequisites) operation once to grant permissions 
from the AAD application to MSFT Graph API. This operation should be applied with a user with [Global Admin](https://learn.microsoft.com/en-us/azure/active-directory/roles/permissions-reference#global-administrator). 
Please follow instructions on *TODO - setup ..* markdown document generated after 
deploying the Terraform project to apply these operations and for further information about
what permission is using each connector.

## Troubleshooting

### DEPRECATED: Certificate creation via Terraform

**DEPRECATED** - will be removed in v0.5; this is not recommended approach, for a variety of
reasons, since Microsoft release support for federated credentials in ~Sept 202. See our module
`azuread-federated-credentials` for preferred alternative.

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