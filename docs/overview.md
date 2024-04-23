# Overview

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
