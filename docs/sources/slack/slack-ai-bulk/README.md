# Slack AI Snapshot

Psoxy can pseudonymize Slack AI Snapshot data for ingestion into Worklytics.

See [https://docs.worklytics.co/knowledge-base/connectors/bulk-data/slack-ai-snapshot](https://docs.worklytics.co/knowledge-base/connectors/bulk-data/slack-ai-snapshot)

The default proxy rules for `slack-ai-snapshot` will pseudonymize `user_email`. If your data set does not match
the schema expected by Worklytics, you can adapt it by specifying some custom transforms within
the proxy itself:

```hcl
custom_bulk_connector_rules = {
   "slack-ai-bulk" = {
        source_kind               = "slack-ai",
        worklytics_connector_id   = "bulk-import-psoxy"
        worklytics_connector_name = "Bulk Import - Psoxy"
        display_name              = "Slack Bulk"
        rules = {
          columnsToPseudonymize = ["user_email"]
          }
        settings_to_provide = {
           "Parser" = "slack-ai-bulk"
          }
  }
}
```
