# Slack Bulk Imports

This connector supports organizations who already maintain replicas of their Slack data on-prem, such as in cloud storage or a data lake, to import that data in bulk to Worklytics.

## Examples

- [Example Rules: bulk](discovery-bulk.yaml)
- [Example Rules: bulk hierarchical](discovery-bulk-hierarchical.yaml)
- Example Data : [users.ndjson](example-bulk/original/users.ndjson) |
  [users.ndjson](example-bulk/sanitized/users.ndjson)

### Slack Bulk Imports

As an alternative to connecting Worklytics to the Slack via an API , it is possible to use the bulk-mode of the proxy to sanitize an export of Slack data and ingest the resulting sanitized data to Worklytics. Example data of this is given in the [`example-bulk/`](example-bulk/) folder.

This data can be processing using custom multi-file type rules in the proxy, of which [`discovery-bulk.yaml`](discovery-bulk.yaml) is an example.

For clarity, example files are NOT compressed, so don't have `.gz` extension; but rules expect `.gz`. For performance reasons, we strongly recommend compressing the files before transferring them to Worklytics and cannot warranty the performance of this connector if you do not.
