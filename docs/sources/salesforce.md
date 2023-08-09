# Salesforce

## Examples

* [Example Rules](example-rules/salesforce/salesforce.yaml)
* Example Data : [original](api-response-examples/salesforce) | [sanitized](api-response-examples/salesforce/sanitized)

## Steps to Connect

  1. Create a [Salesforce application + client credentials flow](https://help.salesforce.com/s/articleView?language=en_US&id=sf.remoteaccess_oauth_client_credentials_flow.htm&type=5)
     with following permissions:
     - Manage user data via APIs (`api`)
     - Access Connect REST API resources (`chatter_api`)
     - Perform requests at any time (`refresh_token`, `offline_access`)
     - Access unique user identifiers (`openid`)
     - Access Lightning applications (`lightning`)
     - Access content resources (`content`)
     - Perform ANSI SQL queries on Customer Data Platform data (`cdp_query_api`)

     Apart from Salesforce instructions above, please review the following:
     - "Callback URL" MUST be filled; can be anything as not required in this flow, but required to be set by Salesforce.
     - Application MUST be marked with "Enable Client Credentials Flow"
     - You MUST assign a user for Client Credentials, be sure:
        - you associate a "run as" user marked with "API Only Permission"
        - The policy associated to the user MUST have the following Administrative Permissions enabled:
          - API Enabled
          - APEX REST Services
      - The policy MUST have the application marked as "enabled" in "Connected App Access". Otherwise requests will return 401 with INVALID_SESSION_ID

     The user set for "run as" on the connector should have, between its `Permission Sets` and `Profile`, the permission of `View All Data`. This is required
     to support the queries used to retrieve [Activity Histories](https://developer.salesforce.com/docs/atlas.en-us.object_reference.meta/object_reference/sforce_api_objects_activityhistory.htm) by *account id*.

   3. Once created, open "Manage Consumer Details"
   4. Update the content of `PSOXY_SALESFORCE_CLIENT_ID` from Consumer Key	and `PSOXY_SALESFORCE_CLIENT_SECRET` from Consumer Secret
   5. Finally, we recommend to run `test-salesforce` script with all the queries in the example to ensure the expected information covered by rules can be obtained from Salesforce API.

NOTE: derived from [worklytics-connector-specs](../../infra/modules/worklytics-connector-specs/main.tf); refer to that for definitive information.
