# Overview

Psoxy is a serverless, pseudonymizing, Data Loss Prevention (DLP) layer between Worklytics and your data sources. It acts as a Security / Compliance layer, which you can deploy between your data sources (SaaS tool APIs, Cloud storage buckets, etc) and Worklytics.

Benefits include:
  - Granular authorization on the API endpoint, parameter, and field-levels to your Sources. Eg, limit Worklytics to calling ONLY an explicit subset of an APIs endpoints, with an explicit set of possible parameters, and receiving ONLY a subset fields in response.
  - no API keys for your data sources are ever sent or held by Worklytics.
  - any PII present in your data can be pseudonymized before being sent to Worklytics
  - sensitive data can be redacted before being sent to Worklytics


## Modes

Psoxy can be deployed/used in 4 different modes (deployment scenarios), to support various data flows from sources.

### API Mode

- **API** - psoxy sits in front of a data source API. Any call that would normally be sent to the data source API is instead sent to psoxy, which parses the request, validates it / applies ACL, and adds authentication before forwarding to the host API. After the host API response, psoxy sanitizes the response as defined by its roles before returning the response to the caller. This is an _http triggered_ flow.

For some connectors, an **'async'** variant of this is is supported; if client requests `Prefer: respond-async`, psoxy may responds `202 Accepted` and provide a cloud storage uri (s3, gcs, etc) were actual response will be available after being asynchronously requested from source API and sanitized.

In this mode, the client service (Worklytics) must be able to send HTTPS requests to the proxy instances (either directly, or via an API gateway). If async is enabled, the client service must also access the destination bucket from which to retrieve any data that is processed asynchronously.

More details:
- [Configure API Data Sanitization](./configuration/api-data-sanitization.md)
- [Async API Data](./configuration/async-api-data.md)

### Bulk Data Mode

In **Bulk Data** mode, the proxy is triggered by files (objects) being uploaded to cloud storage buckets (eg, S3, GCS, etc). Psoxy reads the incoming file, applies one or more sanitization rules (transforms), writing the result(s) to a destination (usually in distinct bucket).

The destination bucket is exposed the to client service (Workltyics), from which it can access the data. The client service would typically poll for newly processed data to arrive in the bucket.

More details:
- [Configure Bulk Data Sanitization](./configuration/bulk-file-sanitization.md)

### Webhook Collection

In **Webhook Collection** mode, psoxy is an endpoint for [webhooks](https://en.wikipedia.org/wiki/Webhook), receiving payloads from an app/service over HTTPS POST methods, the content of which validated, sanitized (transformed), and finally written to a destination cloud storage bucket. Webhook requests MUST be authenticated via OIDC. 

The app/service in question is usually an internal / on-prem tool, that lacks a REST or similar API that would be suitable for API mode.

While this mode is designed as a webhook endpoint, it's usefulness is not limited to "real-time" data. You could write a script that "exports" data from a source by POST each element to a webhook collector, as if it were an ingestion API. This might be a more convenient alternative to using "Bulk Data" mode for that use-case in many situations.

As in Bulk Data mode, the destination bucket - which contains only sanitized data - must be accessible to the client service, which must poll for new data appearing in the bucket. The client service does not require any access to the actual proxy instance (cloud function) that processes the data.

More details: [Configure Webhook Sanitization](./configuration/webhook-collectors.md)

### Command-line (cli)

In **Command-line (cli)** mode, psoxy is invoked from the command-line to sanitize data stored in files on the local machine. This is useful for testing, or for one-off data sanitization tasks. Resulting files can then be transferred to a client service via some other means. (Worklytics supports a direct file upload or storage bucket import features, for example; subject to size/format limits) 

This mode is NOT recommended for ongoing production use. It's provided mainly for testing and supporting some edge cases.

## Layers of Data Protection

Data transfer via Psoxy provides a layered approach to data protection, with various redundancies against vulnerabilities / misconfigurations to controls implemented at each layer.

1. **Data source API authorization** The API of your data source limit the data which your proxy instance can access to a set of [oauth scopes](https://oauth.net/2/scope/). Typically, these align to a set of API endpoints that a given authentication credential is authorized to invoke. In some cases, oauth scopes may limit the fields returned in responses from various endpoints.
2. **Host Platform ACL (IAM)**  Your proxy instances will be hosted in your preferred cloud hosting provider (eg, AWS, GCP) and access restricted per your host's ACL capabilities. Typically, this means only **principals** (to borrow AWS's parlance, eg users/roles/etc) which you authorize via an IAM policy can invoke your proxy instances. Apart from limiting *who* can access data via you proxy instance, IAM rules can enforce read-only access to RESTful APIs by limited the allowed HTTP methods to `GET`/`HEAD`/etc.
3. **Proxy-level ACL** Psoxy itself offers a sophisticated set of access restriction rules, including limiting access by:
       - HTTP method (eg, limit to `GET`/`HEAD` to ensure read-only access)
       - API endpoint (eg, limit access to `/files/{fileId}/metadata`)
       - API parameter (eg, allow only `page,pageSize` as parameters)
4. **Proxy-level response transformation** Psoxy can be configured to sanitize fields in API responses, including:
      - pseudonymizing/tokenizing fields that include PII or sensitive identifiers
      - redacting fields containing sensitive information or which aren't needed for analysis
5. **Network-level Client IP restriction** Optionally, proxy instances may be locked to be invoked ONLY from a given set of IP addresses, leveraging the capabilities of the host service. This may require using VPC/API Gateway features of the hose platform, as well as a subscription with Worklytics that ensures a static list of IPs from which data transfer requests will originate. 

Together, these layers of data protection can redundantly control data access. Eg, you could ensure read-only access to GMail metadata by:
  - granting the Gmail metadata-only oauth scope to your instance via the Google Workspace Admin console, instead of the full Gmail API scope
  - restricting only `GET` requests to your proxy instance via AWS IAM policy
  - configure rules in your Proxy instance allow only `GET` requests to be sent to Gmail API via your instances; and only to eht `/gmail/v1/users/{mailboxId}/messages` and `/gmail/v1/users/{mailboxId}/messages/{messageId}` endpoints
  - configure rules in your Proxy instance that filter responses to an explicit set of metadata fields form those endpoints

This example illustrates how the proxy provides data protection across several redundant layers, each provided by different parties. Eg:
   - you trust Google to correctly implement their oauth scopes and API access controls to limit the access to gmail metadata
   - you trust AWS to correctly implement their IAM service, enforcing IAM policy to limit data access to the methods and principals you configure.
   - you trust the Psoxy implementation, which is source-available for your review and testing, to properly implement its specified rules/functionality.
   - you trust Worklytics to implement its service to not store or process non-metadata fields, even if accessible.

You can verify this trust via the logging provided by your data source (API calls received), your cloud host (eg, AWS cloud watch logs include API calls made via the proxy instance), the psoxy testing tools to simulate API calls and inspect responses, and Worklytics logs.

## Software Bill of Materials (SBOM)

For transparency and security auditing, we provide comprehensive Software Bill of Materials (SBOM) for each platform deployment:

- [AWS SBOM](aws/sbom.json) - Complete inventory of all dependencies in the AWS Lambda deployment (~115 components)
- [GCP SBOM](gcp/sbom.json) - Complete inventory of all dependencies in the GCP Cloud Function deployment (~133 components)

The SBOMs are provided in [CycloneDX](https://cyclonedx.org/) v1.5 format, an industry-standard machine-readable specification maintained by OWASP. Each SBOM includes:
- All Java libraries and transitive dependencies
- License information for each component
- Cryptographic hashes for verification
- Package URLs (PURLs) for standardized identification
- Dependency relationship mappings

**Use Cases:**
- **Security Auditing** - Scan for known vulnerabilities using tools like [Grype](https://github.com/anchore/grype), [Trivy](https://github.com/aquasecurity/trivy), or [OWASP Dependency-Check](https://owasp.org/www-project-dependency-check/)
- **Compliance** - Generate reports for SOC 2, ISO 27001, and other compliance requirements
- **Vulnerability Management** - Track CVEs affecting your deployment and prioritize patching efforts
- **Supply Chain Security** - Understand transitive dependencies and assess supply chain risks

**Regenerating SBOMs:**
```bash
# From repository root
./tools/release/generate-sbom.sh
```

SBOMs are automatically regenerated during releases and available as GitHub Actions artifacts for each release.
