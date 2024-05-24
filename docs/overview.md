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
