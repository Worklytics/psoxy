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

     Apart from Salesforce instructions please review the following:
     - "Callback URL" can be anything, not required in this flow but required by Salesforce.
     - Application is marked with "Enable Client Credentials Flow"
     - You have to assign a user for Client Credentials, be sure:
        - A "run as" user marked with "API Only Permission" needs to be associated
        - The policy associated to the user have the enabled next Administrative Permissions:
          - API Enabled
          - APEX REST Services
      - And the policy has the application created marked as enabled in "Connected App Access". Otherwise requests will return 401 with INVALID_SESSION_ID

    The user set up as "run as" on the connector should have between `Permission Sets` and `Profile` the permission of `View All Data`. This is due the kind of queries supported
    for retrieving [Activity Histories](https://developer.salesforce.com/docs/atlas.en-us.object_reference.meta/object_reference/sforce_api_objects_activityhistory.htm) based on the
    related *account id*.

   2. Once created, open "Manage Consumer Details"
   3. Update the content of `PSOXY_SALESFORCE_CLIENT_ID` from Consumer Key	and `PSOXY_SALESFORCE_CLIENT_SECRET` from Consumer Secret
   4. Finally, we recommend to run `test-salesforce` script with all the queries in the example to ensure the expected information covered by rules can be obtained from Salesforce API.

NOTE: derived from [worklytics-connector-specs](../../infra/modules/worklytics-connector-specs/main.tf); refer to that for definitive information.