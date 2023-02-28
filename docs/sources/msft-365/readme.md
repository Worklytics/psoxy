# Microsoft 365

## Required roles
Examples `msft-365` will create an *Azure Active Directory* (AAD) application if there is any MSFT connector enabled, such
as `azure-ad`, `outlook-cal` or `outlook-mail`. The AAD application created will have the right permissions to access to
MSFT Graph API to perform the requests from proxy.

Users will require following at least following roles enabled in its account:
- [Cloud Application Administrator](https://learn.microsoft.com/en-us/azure/active-directory/roles/permissions-reference#cloud-application-administrator). This is to create/update/delete AAD applications and its settings during Terraform apply command.
- [Privileged Role Administrator](https://learn.microsoft.com/en-us/azure/active-directory/roles/permissions-reference#privileged-role-administrator) to perform [admin consent](https://learn.microsoft.com/en-us/azure/active-directory/manage-apps/grant-admin-consent?pivots=ms-graph#prerequisites) operation once
to grant permission access to MSFT Graph API from the AAD application. This has to be done after Terraform execution, following our instructions on *TODO - setup ..* markdown document generated after
deploying the Terraform project. That document can be used as well to have further details about permissions required per each connector.

## Troubleshooting

### DEPRECATED: Certificate creation via Terraform

**DEPRECATED** - will be removed in v0.5; this is not recommended approach, for a variety of
reasons, since Microsoft release support for federated credentials in ~Sept 2022. See our module
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