# Google Workspace&trade;

Google Workspace&trade; sources can be setup via Terraform, using modules found in our GitHub repo.

As of August 2023, we suggest you use one of our template repos, eg:

- [`aws`](https://github.com/Worklytics/psoxy-example-aws)
- [`gcp`](https://github.com/Worklytics/psoxy-example-gcp)

Within those, the `google-workspace.tf` and `google-workspace-variables.tf` files specify the terraform configuration to use Google Workspace&trade; sources.

## Available connectors

- [calendar](calendar/README.md) (Google Calendar&trade;)
- [directory](directory/README.md) (Google Workspace&trade; Directory)
- [gdrive](gdrive/README.md) (Google Drive&trade;)
- [gemini-in-workspace-apps](gemini-in-workspace-apps/README.md)
- [gemini-usage-bulk](gemini-usage-bulk/README.md)
- [gmail](gmail/README.md) (Gmail&trade;)
- [google-chat](google-chat/README.md) (Google Chat&trade;)
- [meet](meet/README.md) (Google Meet&trade;)

OAuth scopes in the table below omit the `https://www.googleapis.com/auth/` prefix. See [OAuth 2.0 Scopes for Google APIs](https://developers.google.com/identity/protocols/oauth2/scopes). Definitive values are defined in [`google-workspace.tf`](../../../infra/modules/worklytics-connector-specs/google-workspace.tf).

Each connector page includes the full comma-separated OAuth scope string to paste into the Google Workspace Admin console when granting Domain-wide Delegation.

| Connector | Connector ID | GCP APIs to Enable | OAuth Scopes |
|-----------|--------------|--------------------|--------------|
| [calendar](calendar/README.md) | `gcal` | `calendar-json.googleapis.com` | `calendar.readonly` |
| [google-chat](google-chat/README.md) | `google-chat` | `admin.googleapis.com` | `admin.reports.audit.readonly` |
| [directory](directory/README.md) | `gdirectory` | `admin.googleapis.com` | `admin.directory.user.readonly` `admin.directory.domain.readonly` `admin.directory.group.readonly` `admin.directory.orgunit.readonly` |
| [gdrive](gdrive/README.md) | `gdrive` | `drive.googleapis.com` | `drive.readonly` (v0.7.0+; see [gdrive README](gdrive/README.md#scope-change-in-v070-action-required)) |
| [gmail](gmail/README.md) | `gmail` | `gmail.googleapis.com` | `gmail.metadata` |
| [meet](meet/README.md) | `google-meet` | `admin.googleapis.com` | `admin.reports.audit.readonly` |
| [gemini-in-workspace-apps](gemini-in-workspace-apps/README.md) | `gemini-in-workspace-apps` | `admin.googleapis.com` | `admin.reports.audit.readonly` |
| [gemini-usage-bulk](gemini-usage-bulk/README.md) | `gemini-usage` | n/a (bulk CSV upload) | n/a (bulk CSV upload) |

Enable the listed GCP APIs in the project where you provision each OAuth client. If you use our Terraform modules with `enable_apis = true` (the default), this is done automatically; otherwise, enable them via the GCP console ("APIs & Services" → "Library") or `gcloud services enable`.

### Domain-wide Delegation scope strings

When granting Domain-wide Delegation in the Google Workspace Admin console, paste the connector's comma-separated OAuth scope string into the **Scopes** field. Use the full `https://www.googleapis.com/auth/...` URLs (as shown below and on each connector page), not the short form from the table above.

If you share a **single OAuth client** across multiple connectors (see [Provisioning API clients without Terraform](#provisioning-api-clients-without-terraform)), enable the union of all required GCP APIs and grant the superset of OAuth scopes:

```
https://www.googleapis.com/auth/calendar.readonly,https://www.googleapis.com/auth/admin.directory.user.readonly,https://www.googleapis.com/auth/admin.directory.domain.readonly,https://www.googleapis.com/auth/admin.directory.group.readonly,https://www.googleapis.com/auth/admin.directory.orgunit.readonly,https://www.googleapis.com/auth/drive.readonly,https://www.googleapis.com/auth/gmail.metadata,https://www.googleapis.com/auth/admin.reports.audit.readonly
```

As of v0.7.0 the gdrive connector requires `drive.readonly` instead of `drive.metadata.readonly`. If you already granted this shared string, update the Domain-wide Delegation grant (see [gdrive README](gdrive/README.md#scope-change-in-v070-action-required)).

Required GCP APIs for the single shared OAuth client:

- `admin.googleapis.com`
- `calendar-json.googleapis.com`
- `drive.googleapis.com`
- `gmail.googleapis.com`

## Required Permissions

You (the user running Terraform) must have the following roles (or some of the permissions within them) in the GCP project in which you will provision the OAuth clients that will be used to connect to your Google Workspace&trade; data:

| Role                                                                                                          | Reason                                                                                         |
| ------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| [Service Account Creator](https://cloud.google.com/iam/docs/understanding-roles#iam.serviceAccountCreator)    | create Service Accounts to be used as API clients                                              |
| [Service Account Key Admin](https://cloud.google.com/iam/docs/understanding-roles#iam.serviceAccountKeyAdmin) | to access Google Workspace&trade; API, proxy _must_ be authenticated by a key that you need to create |
| [Service Usage Admin](https://cloud.google.com/iam/docs/understanding-roles#serviceusage.serviceUsageAdmin)   | you will need to enable the Google Workspace&trade; APIs in your GCP Project                          |

As these are very permissive roles, we recommend that you use a _dedicated_ GCP project so that these roles are scoped just to the Service Accounts used for this deployment. If you used a shared GCP project, these roles would give you access to create keys for ALL the service accounts in the project, for example - which is not good practice.

Additionally, a Google Workspace&trade; Admin will need to make a Domain-wide Delegation grant to the Oauth Clients you create. This is done via the Google Workspace&trade; Admin console. In default setup, this requires [Super Admin](https://support.google.com/a/answer/2405986?hl=en&fl=1) role, but your organization may have a Custom Role with sufficient privileges.

## Google Workspace&trade; User for Connection

We also recommend you create a dedicated Google Workspace&trade; user for Psoxy to use when connecting to your Google Workspace&trade; Admin API, with the specific permissions needed. This avoids the connection being tied to a personal account and helps with auditing and security.

This is not to be confused with a GCP Service Account. Rather, this is a regular Google Workspace&trade; user account, but intended to be assigned to a service rather than a human user. Your proxy instance will impersonate this user when accessing the [Google Admin Directory](https://developers.google.com/admin-sdk/directory/v1/guides) and [Reports](https://developers.google.com/workspace/admin/reports) APIs. (Google requires that these be accessed via impersonation of a Google user account, rather than directly using a GCP service account).

We recommend naming the account `svc-worklytics@{your-domain.com}`.

If you have already created a sufficiently privileged service account user for a different Google Workspace&trade; connection, you can re-use that one.

Assign the account a sufficiently privileged role. At minimum, the role must grant _read-only_ access to the following [Administrator privileges](https://knowledge.workspace.google.com/admin/users/administrator-privilege-definitions) (expand each category in the Custom Role editor and enable only the **Read** sub-action, rather than checking the parent checkbox):

| Privilege | Required? | Purpose |
| --------- | --------- | ------- |
| **Users** → Read | Yes | Directory user data |
| **Groups** → Read | Yes | Directory group membership |
| **Organizational Units** → Read | Optional | Org-unit segmentation |
| **Domain Management** | Optional | List of internal domains |
| **Reports** | Only if using [Google Chat](google-chat/README.md), [Google Meet](meet/README.md), or other audit-log connectors | Audit / usage reports |

All of the above are found under **Admin settings privileges** in the Custom Role editor. Google reorganized administrator privileges in 2025; expand each category and enable only the **Read** sub-action where available. See Google's [privilege definitions](https://knowledge.workspace.google.com/admin/users/administrator-privilege-definitions) for the full list.

The email address of the account you created will be used when creating the data connection to the Google Directory in the Worklytics&trade; portal. Provide it as the value of the 'Google Account to Use for Connection' setting when they create the connection.

### Custom Role

If you choose not to use a predefined role that covers the above, you can define a [Custom Role](https://support.google.com/a/answer/2406043?fl=1).

Using a Custom Role with read-only access to each required privilege is good practice, but least-privilege is also enforced in TWO additional ways:

- the Proxy API rules restrict the API endpoints that Worklytics&trade; can access, as well as the HTTP methods that may be used. This enforces read-only access, limited to the required data types (and actually even more granular that what Workspace Admin privileges and OAuth Scopes support).
- the Oauth Scopes granted to the API client via Domain-wide delegation. Each OAuth Client used by Worklytics&trade; is granted only read-only scopes, least-permissive for the data types required. eg `https://www.googleapis.com/auth/admin.directory.users.readonly`.

So a least-privileged custom role is essentially a 3rd layer of enforcement.

An example least-privilege Custom Role for the Directory connector:

![custom-role-least-privilege.png](custom-role-least-privilege.png)

**YMMV** - Google's UI changes frequently and varies by Google Workspace&trade; edition, so you may see more or fewer options than shown above. Scroll the privilege list and enable only the **Read** sub-actions required for your connectors.

## General Authentication Overview

Google Workspace&trade; APIs use OAuth 2.0 for authentication and authorization. You create an Oauth 2.0 client in Google Cloud Platform and a credential (service account key), which you store in as a secret in your Proxy instance.

When the proxy connects to Google, it first authenticates with Google API using this secret (a service account key) by signing a request for a short-lived access token. Google returns this access token, which the proxy then uses for subsequent requests to Google's APIS until the token expires.

The service account key can be rotated at any time, and the terraform configuration examples we provide can be configured to do this for you if applied regularly.

More information: [https://developers.google.com/workspace/guides/auth-overview](https://developers.google.com/workspace/guides/auth-overview)

To initially authorize each connector, a sufficiently privileged Google Workspace&trade; Admin must make a Domain-wide Delegation grant to the Oauth Client you create, by pasting its numeric ID and a CSV of the required OAuth Scopes into the Google Workspace&trade; Admin console. This is a one-time setup step.

If you use the provided Terraform modules (namely, `google-workspace-dwd-connection`), a TODO file with detailed instructions will be created for you, including the actual numeric ID and scopes required.

Note that while Domain-wide Delegation is a broad grant of data access, the implementation of it in proxy is mitigated in several ways because the GCP Service Account resides in your own GCP project, and remains under your organizes control - unlike the most common Domain-wide Delegation scenarios which have been the subject of criticism by security researchers. In particular:

- you may directly verify the numeric ID of the service account in the GCP web console, or via the GCP CLI; you don't need to take our word for it.
- you may monitor and log the use of each service account and its key as you see fit.
- you can ensure there is never more than one active key for each service account, and rotate keys at any time.
- the key is only used from infrastructure (GCP CLoud Function or Lambda) in your environment; you should be able to reconcile logs and usage between your GCP and AWS environments should you desire to ensure there has been no malicious use of the key.

### Provisioning API clients without Terraform

While not recommended, it is possible to set up Google API clients without Terraform, via the GCP web console.

1. Create or choose the GCP project in which to create the OAuth Clients.
2. Activate relevant API(s) in the project.
3. Create a Service Account in the project; this will be the OAuth Client.
4. Get the numeric ID of the service account. Use this plus the oauth scopes to make domain-wide delegation grants via the Google Workspace admin console.

Then follow the steps in the next section to create the keys for the Oauth Clients.

If your organization's policies don't allow Terraform to manage some or all of these GCP resources, you can still use our Terraform modules for the rest of your deployment and disable the parts you must do manually via `google_workspace_connector_settings` in your `terraform.tfvars`:

```hcl
google_workspace_connector_settings = {
  enable_apis                = false
  provision_service_accounts = false
  provision_keys             = false
}
```

When any of these are `false`, Terraform will skip creating the corresponding resources and instead emit TODO files (or `todos_1` outputs, if configured) with instructions to complete those steps outside of Terraform.

NOTE: if you are creating connections to multiple Google Workspace&trade; sources, you can use a single OAuth client and share it between all the proxy instances. You just need to authorize the entire superset of Oauth scopes required by those connnections for the OAuth Client via the Google Workspace&trade; Admin console.

### Provisioning API Keys without Terraform

If your organization's policies don't allow GCP service account keys to be managed via Terraform (or you lack the perms to do so), you can still use our Terraform modules to create the clients, and just add the following to your `terraform.tfvars` to disable provisioning of the keys:

```hcl
google_workspace_connector_settings = {
  provision_keys = false
}
```

The deprecated top-level variable `google_workspace_provision_keys` is still supported, but the map form above is preferred.

Then you can create the keys manually, and store them in your secrets manager of choice.

For each API client you need to:

1. Create a JSON key for the service account (via GCP console or CLI)
2. Base64-encode the key; eg `cat service-account.json | base64 | pbcopy`
3. store it as a secret named should be something like `PSOXY_GDIRECTORY_SERVICE_ACCOUNT_KEY`. Our Terraform modules should still create an instance of the secret in your host environment, just filled with a placeholder value.

For GCP Secrets manager, you can do (3) via CLI as follows:
`pbpaste | gcloud secrets versions add PSOXY_GCAL_SERVICE_ACCOUNT_KEY --data-file=- --project=YOUR_PROJECT_ID`

For AWS Systems Manager Parameter Store, you can do (3) via CLI as follows:
`pbpaste | aws ssm put-parameter --name PSOXY_GCAL_SERVICE_ACCOUNT_KEY --type SecureString --value - --region us-east1`

(NOTE: please refer to aws/gcloud docs for exact versions of commands above; YMMV, as this is not our recommended approach for managing keys)

If you are sharing a single OAuth client between multiple proxy instances, you just repeat step (3) for EACH client. (eg, store N copies of the key, all with the same value)

Whenever you want to rotate the key (which GCP recommends at least every 90 days), you must repeat the steps in this section (no need to create Service Account again; just create a new key for it and put the new version into Secrets Manager).

## Domain-wide Delegation Alternative

If you remain uncomfortable with Domain-wide Delegation, a private Google Marketplace App is a possible, if tedious and harder to maintain, alternative. Here are some trade-offs:

Pros:

- Google Workspace&trade; Admins may perform a single Marketplace installation, instead of multiple DWD grants via the admin console
- "install" from the Google Workspace&trade; Marketplace is less error-prone/exploitable than copy-paste a  numeric service account ID
- visual confirmation of the oauth scopes being granted by the install
- ability to "install" for an Org Unit, rather than the entire domain

Cons:

- you must use a dedicated GCP project for the Marketplace App; "installation" of a Google Marketplace App grants all the service accounts in the project access to the listed oauth scopes. You must understand the OAuth grant is to the project, not a specific service account.
- you must enable additional APIs in the GCP project (marketplace SDK).
- as of Dec 2023, Marketplace Apps cannot be completely managed by Terraform resources; so there are more out-of-band steps that someone must complete by hand to create the App; and a simple `terraform destroy` will not remove the associated infrastructure. In contrast, `terraform destroy` in the DWD approach will result in revocation of the access grants when the service account is deleted.
- You must monitor how many service accounts exist in the project and ensure only the expected ons  are created. Note that all Google Workspace&trade; API access, as of Dec 2023, requires the service account to authenticate with a key; so any SA without a key provisioned cannot access your data.


## Troubleshooting

Match log / response signals to a setup issue. Token failures occur on `POST https://oauth2.googleapis.com/token` and usually set `X-Psoxy-Error: CONNECTION_SETUP`. Failures calling a Workspace API (`admin.googleapis.com`, `www.googleapis.com`, etc.) usually set `X-Psoxy-Error: API_ERROR` and may echo Google's error JSON in the response body.

| Signal | Condition | Fix |
|--------|-----------|-----|
| `403` — `usageLimits` — `accessNotConfigured`; or `SERVICE_DISABLED`; or `has not been used in project` … `disabled` | GCP API not enabled | [Required GCP APIs](#available-connectors) |
| `401 Unauthorized` on `oauth2.googleapis.com/token`; or `"error":"unauthorized_client"` | DWD not granted, wrong Client ID, **or** granted scopes ≠ `OAUTH_SCOPES` | [Domain-wide Delegation scope strings](#domain-wide-delegation-scope-strings) |
| `"error":"access_denied"` or `"error":"invalid_scope"` on `oauth2.googleapis.com/token` (some scope mismatches appear as `401` instead) | `OAUTH_SCOPES` not covered by DWD grant | Connector README OAuth scopes |
| `"error":"invalid_grant"` + `Invalid JWT Signature` / `SignatureException` on `oauth2.googleapis.com/token` | SA key wrong, revoked, or rotated | [Provisioning API Keys without Terraform](#provisioning-api-keys-without-terraform) |
| `IllegalArgumentException` parsing service account key secret | Malformed key secret (not JSON / not base64 JSON) | [Provisioning API Keys without Terraform](#provisioning-api-keys-without-terraform) |
| `403` + `insufficientPermissions` (after token succeeds) | Impersonated user lacks Workspace admin privileges | [Google Workspace User for Connection](#google-workspace-user-for-connection) |

### `403` — `usageLimits` — `accessNotConfigured`

GCP API not enabled for the OAuth client project. Signals in the Google error JSON / proxy logs:

```json
"errors": [{ "domain": "usageLimits", "reason": "accessNotConfigured" }]
```

```json
"details": [{ "reason": "SERVICE_DISABLED", "metadata": { "service": "admin.googleapis.com", "serviceTitle": "Admin SDK API" } }]
```

```json
"message": "Admin SDK API has not been used in project 123456789012 before or it is disabled."
```

**Fix:** [Required GCP APIs](#available-connectors).

### `401` — `oauth2.googleapis.com/token`

DWD missing, wrong Client ID, or scopes that do not match `OAUTH_SCOPES`:

```
Error getting access token for service account: 401 Unauthorized
POST https://oauth2.googleapis.com/token, iss: psoxy-example-google-meet@example-project.iam.gserviceaccount.com
```

```json
{ "error": "unauthorized_client", "error_description": "Unauthorized client or scope in request." }
```

NOTE: Google does not always distinguish a missing DWD grant from a **scope mismatch** (DWD granted, but with different scopes than `OAUTH_SCOPES`). A wrong-scope grant can produce the same `401 Unauthorized` stack trace as no grant at all — compare `OAUTH_SCOPES` on the proxy against the scopes listed in the Admin console for this Client ID.

**Fix:** [Domain-wide Delegation scope strings](#domain-wide-delegation-scope-strings) and connector README OAuth scopes.

### `400` — `access_denied` — `oauth2.googleapis.com/token`

`OAUTH_SCOPES` requests scopes not in the DWD grant. Some mismatches surface as `401` instead — see above.

```
Error getting access token for service account: 400 Bad Request POST https://oauth2.googleapis.com/token
```

```json
{ "error": "access_denied" }
```

Proxy may also log: `Confirm OAUTH_SCOPES environment variable matches scopes granted in data source`.

**Fix:** Connector README OAuth scopes; note `OAUTH_SCOPES` uses spaces, Admin console uses commas.

### `400` — `invalid_grant` — `oauth2.googleapis.com/token`

Service account key wrong, revoked, rotated, or not matching the provisioned account:

```json
{ "error": "invalid_grant", "error_description": "Invalid JWT Signature." }
```

```
java.security.SignatureException: Invalid signature for token
```

**Fix:** [Provisioning API Keys without Terraform](#provisioning-api-keys-without-terraform).

### `invalid_scope` — `oauth2.googleapis.com/token`

Malformed or non-existent scope URL in `OAUTH_SCOPES`:

```json
{ "error": "invalid_scope", "error_description": "Invalid OAuth scope or ID token audience provided." }
```

**Fix:** [`google-workspace.tf`](../../../infra/modules/worklytics-connector-specs/google-workspace.tf) and connector README.

See also Google's [JWT error codes](https://developers.google.com/identity/protocols/oauth2/service-account#jwt-error-codes).

---
Google Workspace&trade; and related marks are trademarks of Google LLC.



Worklytics&trade; is a trademark of Worklytics, Corp.
