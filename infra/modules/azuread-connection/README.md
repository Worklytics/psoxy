# azuread-connection

This module registers an Azure AD Application in a Microsoft 365 tenant.

## Troubleshooting

### 'Disabled' Apps

NOTE: in some environments, we've seen newly created Azure Apps in a 'Disabled' state, for no clear
reason.

This results in errors from Microsoft as follows:
```json
{
  "error": "unauthorized_client",
  "error_description": "AADSTS7000112: Application '8b0c024c-7823-3774-2da8-a8d8b7ed331a'(worklytics-azuread-connector) is disabled."
}
```

This can be solved by the following:

```powershell
Set-AzureADServicePrincipal -ObjectId {{objectId}} -AccountEnabled $true
```


