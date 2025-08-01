# ChatGPT

## **ALPHA** Compliance API

This API allows ChatGPT Enterprise administrators to observe and remove data from their ChatGPT Enterprise workspaces. It is intended for compliance, security, and data privacy use.  The get endpoints provide time-indexed access to ChatGPT Enterprise data.
Administrators can use it to retrieve this data for archival or Data Loss Prevention purposes as part of a compliance or security program.

This API is designed for regularly downloading data diffs to synchronize an offline database for data compliance, rather than mass export. The initial data sync will take longer, but subsequent syncs will be faster as only the diffs need to be downloaded.

Owners can generate an API key in the [OpenAI API Platform Portal](https://platform.openai.com/api-keys).
Note that the correct Organization must be selected when creating a key, corresponding to the  administered workspace.
Do not select the owner's personal organization.

## Examples

- [Example Rules](compliance/chatgpt-compliance.yaml)
- Example Data : [compliance/original/conversations.json](compliance/example-api-responses/original/conversations.json) |
  [sanitized/conversations.json](compliance/example-api-responses/sanitized/conversations.json)

See more examples in the `docs/sources/chatgpt/compliance/example-api-responses` folder
of the [Psoxy repository](https://github.com/Worklytics/psoxy).
