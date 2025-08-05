# ChatGPT

## **ALPHA** ChatGPT Enterprise via Compliance API


NOTE: As of August 2025, the Compliance API is available ONLY for ChatGPT Enterprise subscriptions. Your organization must have such a subscription to utilize this connector. We cannot warrant current or future availability of this API. Please speak with your OpenAI account representative if you have doubts or questions.

This API is designed for regularly downloading data diffs to synchronize an offline database for data compliance, rather than mass export. The initial data sync will take longer, but subsequent syncs will be faster as only the diffs need to be downloaded.

Owners must generate an API key in the [OpenAI API Platform Portal](https://platform.openai.com/api-keys).  The correct Organization must be selected when creating a key, corresponding to the  administered workspace. Do not select the owner's personal organization.

## Examples

- [Example Rules](enterprise/chatgpt-compliance.yaml)
- Example Data : [enterprise/original/conversations.json](enterprise/example-api-responses/original/conversations.json) |
  [sanitized/conversations.json](enterprise/example-api-responses/sanitized/conversations.json)

See more examples in the `docs/sources/chatgpt/enterprise/example-api-responses` folder of the [Psoxy repository](https://github.com/Worklytics/psoxy).
