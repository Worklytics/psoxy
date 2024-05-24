# Overview

Psoxy is a serverless, pseudonymizing, Data Loss Prevention (DLP) layer  between Worklytics and your
data sources. It acts as a Security / Compliance layer, which you can deploy between your data sources
(SaaS tool APIs, Cloud storage buckets, etc) and Worklytics.

Benefits include:
  - Granular authorization on the API endpoint, parameter, and field-levels to your Sources. Eg, limit
    Worklytics to calling ONLY an explicit subset of an APIs endpoints, with an explicit set of
    possible parameters, and receiving ONLY a subset fields in response.
  - no API keys for your data sources are ever sent or held by Worklytics.
  - any PII present in your data can be pseudonymized before being sent to Worklytics
  - sensitive data can be redacted before being sent to Worklytics


## Modes

Psoxy can be deployed/used in 3 different modes:

- **API** - psoxy sits in front of a data source API. Any call that would normally be sent to the
  data source API is instead sent to psoxy, which parses the request, validates it / applies ACL,
  and adds authentication before forwarding to the host API. After the host API response, psoxy
  sanitizes the response as defined by its roles before returning the response to the caller. This
  is an _http triggered_ flow.
- **Bulk File** - psoxy is triggered by files (objects) being uploaded to cloud storage buckets (eg,
  S3, GCS, etc). Psoxy reads the incoming file, applies one or more sanitization rules (transforms),
  writing the result(s) to a destination (usually in distinct bucket).
- **Command-line (cli)** - psoxy is invoked from the command-line, and is used to sanitize data
  stored in files on the local machine. This is useful for testing, or for one-off data sanitization
  tasks. Resulting files can be uploaded to Worklytics via the file upload of its web portal.


## Layers of Data Protection

Data transfer via Psoxy provides a layered approach to data protection, with various redundancies
against vulnerabilities / misconfigurations to controls implemented at each layer.

1. **Data source API authorization** The API of your data source limit the data which your proxy
   instance can access to a set of [oauth scopes](https://oauth.net/2/scope/). Typically, these
   align to a set of API endpoints that a given authentication credential is authorized to invoke.
   In some cases, oauth scopes may limit the fields returned in responses from various endpoints.
2. **Host Platform ACL (IAM)**  Your proxy instances will be hosted in your preferred cloud hosting
   provider (eg, AWS, GCP) and access restricted per your host's ACL capabilities. Typically, this
   means only **principals** (to borrow AWS's parlance, eg users/roles/etc) which you authorize
   via an IAM policy can invoke your proxy instances. Apart from limiting *who* can access data via
   you proxy instance, IAM rules can enforce read-only access to RESTful APIs by limited the allowed
   HTTP methods to `GET`/`HEAD`/etc.
3. **Proxy-level ACL** Psoxy itself offers a sophisticated set of access restriction rules,
   including limiting access by:
       - HTTP method (eg, limit to `GET`/`HEAD` to ensure read-only access)
       - API endpoint (eg, limit access to `/files/{fileId}/metadata`)
       - API parameter (eg, allow only `page,pageSize` as parameters)
4. **Proxy-level response transformation** Psoxy can be configured to sanitize fields in API
   responses, including:
      - pseudonymizing/tokenizing fields that include PII or sensitive identifiers
      - redacting fields containing sensitive information or which aren't needed for analysis

Together, these layers of data protection can redundantly control data access. Eg, you could ensure
read-only access to GMail metadata by:
  - granting the Gmail metadata-only oauth scope to your instance via the Google Workspace Admin
    console, instead of the full Gmail API scope
  - restricting only `GET` requests to your proxy instance via AWS IAM policy
  - configure rules in your Proxy instance allow only `GET` requests to be sent to Gmail API via
    your instances; and only to eht `/gmail/v1/users/{mailboxId}/messages` and
    `/gmail/v1/users/{mailboxId}/messages/{messageId}` endpoints
  - configure rules in your Proxy instance that filter responses to an explicit set of metadata
    fields form those endpoints

This example illustrates how the proxy provides data protection across several redundant layers, each
provided by different parties. Eg:
   - you trust Google to correctly implement their oauth scopes and API access controls to limit the
     access to gmail metadata
   - you trust AWS to correctly implement their IAM service, enforcing IAM policy to limit data
     access to the methods and principals you configure.
   - you trust the Psoxy implementation, which is source-available for your review and testing, to
     properly implement its specified rules/functionality.
   - you trust Worklytics to implement its service to not store or process non-metadata fields, even
     if accessible.

You can verify this trust via the logging provided by your data source (API calls received), your
cloud host (eg, AWS cloud watch logs include API calls made via the proxy instance), the psoxy
testing tools to simulate API calls and inspect responses, and Worklytics logs.


