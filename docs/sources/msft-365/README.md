# Microsoft 365

## Setup

Connecting to Microsoft 365 data requires:

  1. creating one *Azure Active Directory* (AAD) enterprise application per Microsoft 365 data source (eg, `azure-ad`, `outlook-mail`, `outlook-cal`, etc).
  2. configuring an authentication mechanism to permit each proxy instance to authenticate with
     the Microsoft Graph API. (since Sept 2022, the supported approach is [federated identity credentials](https://learn.microsoft.com/en-us/graph/api/resources/federatedidentitycredentials-overview?view=graph-rest-1.0))
  3. granting [admin consent](https://learn.microsoft.com/en-us/azure/active-directory/manage-apps/grant-admin-consent?pivots=ms-graph#prerequisites) each AAD application access to specific scopes of Microsoft 365 data your connection requires.

Steps (1) and (2) are handled by the `terraform` examples. To perform them, the machine running
`terraform` must be authenticated with [Azure CLI](https://learn.microsoft.com/en-us/cli/azure/) as
an Azure AD user with, at minimum, the following role in your Microsoft 365 tenant:

 - [Cloud Application Administrator](https://learn.microsoft.com/en-us/azure/active-directory/roles/permissions-reference#cloud-application-administrator). This is to create/update/delete AAD applications and its settings during Terraform apply command.

Please note that this role is the least-privileged role sufficient for this task (creating an Azure
AD Application), per Microsoft's documentation. See [Least privileged roles by task in Azure Active Directory](https://learn.microsoft.com/en-us/azure/active-directory/roles/delegate-by-task#enterprise-applications).

This role is needed *ONLY* for the initial `terraform apply` . After each Azure AD enterprise
application is created, the user will be set as the `owner` of that application, providing ongoing
access to read and update the application's settings.  At that point, the general role can be removed.

Step (3) is performed by a Microsoft 365 administrator via the Azure AD web console. Running the
`terraform` examples for steps (1)/(2) will generate a document with specific instructions for this
administrator. This administrator must have, at minimum, the following role in your Microsoft 365
tenant:
  - [Privileged Role Administrator](https://learn.microsoft.com/en-us/azure/active-directory/roles/permissions-reference#privileged-role-administrator)

Again, this is the least-privileged role sufficient for this task (Consent to application
permissions to Microsoft Graph), per Microsoft's documentation. See [Least privileged roles by task in Azure Active Directory](https://learn.microsoft.com/en-us/azure/active-directory/roles/delegate-by-task#enterprise-applications).

## Security

### Authentication
Psoxy uses [Federated Identity Credentials](https://docs.microsoft.com/en-us/graph/api/resources/federatedidentitycredential?view=graph-rest-1.0)
to authenticate with the Microsoft Graph API. This approach avoids the need for any secrets to be
exchanged between your Psoxy instances and your Microsoft 365 tenant. Rather, each API request from
the proxy to Microsoft Graph API is signed by an identity credential generated in your host cloud
platform. You configure your Azure AD application for each connection to trust this identity credential as identifying the
application, and Microsoft trusts your host cloud platform (AWS/GCP) as an external identity
provider of those credentials.

Neither your proxy instances nor Worklytics ever hold any API key or certificate for your Microsoft
365 tenant.

### Authorization and Scopes

The following Scopes are required for each connector. Note that they are all READ-only scopes:

| Source&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; | Application Scopes                                                                                 |
|--------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| Active Directory                                                                                 | `User.Read.All` `Group.Read.All`                                                                   |
| Calendar                                                                                               | `User.Read.All` `Group.Read.All` `OnlineMeetings.Read.All` `Calendars.Read` `MailboxSettings.Read` |
| Mail                                                                                                   | `User.Read.All` `Group.Read.All`  `Mail.ReadBasic.All` `MailboxSettings.Read`                      |

NOTE: the above scopes are copied from [infra/modules/worklytics-connector-specs](../../../infra/modules/worklytics-connector-specs).
They are accurate as of 2023-04-12. Please refer to that module for a definitive list.

NOTE: that `Mail.ReadBasic` affords only access to email metadata, not content/attachments.


## Troubleshooting

### Lack of 'Cloud Application Administrator' role

If you do not have the 'Cloud Application Administrator' role, someone with that or an alternative
role that can create Azure AD applications can create one application per connection and set you
as an owner of each.

You can then `import` these into your Terraform configuration.

First, try `terraform plan | grep 'azuread_application'` to get the Terraform addresses for each
application that your configuration will create.

Second, ask your Microsoft admin to create an application for each of those, set you as the owner,
and send you the `Object ID` for each.

Third, use `terraform import <address> <object-id>` to import each application into your Terraform
state.

At that point, you can run `terraform apply` and it should be able to *update* the applications
with the settings necessary for the proxy to connect to Microsoft Graph API. After that apply,
you will still need a Microsoft 365 admin to perform the admin consent step for each application.

See https://registry.terraform.io/providers/hashicorp/azuread/latest/docs/resources/application#import for details.

### Certificate creation via Terraform **DEPRECATED**

**DEPRECATED** - will be removed in v0.5; this is not recommended approach, for a variety of
reasons, since Microsoft released support for [federated credentials](https://learn.microsoft.com/en-us/graph/api/resources/federatedidentitycredentials-overview?view=graph-rest-1.0) in ~Sept 2022. See our module
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
