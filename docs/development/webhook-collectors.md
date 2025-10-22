# Webhook Collector Dev Notes

## Implementation Design

A new entry point handler; `InboundWebhookHandler` that will handle incoming webhook requests to proxy instances. That will sanitize the payloads and write them to a queue set in `WEBHOOK_OUTPUT` environment variable. From that queue, they'll be ingested in batches and written to the location configured in `WEBHOOK_BATCH_OUTPUT` (see `BatchMergeHandler`).

Rules of type `WebhookCollectionRules`, which includes:
  - `jwtClaimsToVerify` - a list of any JWT claims that must be present in the JWT token sent in `Authorization` header of the incoming webhook request which must be verified against webhook payload before accepting the webhook payload. Keys are the JWT claim names, and value are list of places to check against that claims value
          - eg, `queryParam`, `payloadContent`, `pathParam`, etc
  - `endpoints` - a list of `WebhookEndpoint` objects, which define the endpoints that the webhook collector will which is a list of `WebhookRule` objects; first matching rule will be applied to the incoming webhook request. If none match, collector will return a 400 Bad Request response. Additionally


No matching in v1, so effectively just one `WebhookRule` will have the following properties:
 - `transforms` - a list (ordered) of transforms to apply to the incoming webhook payload before storing it.
 - `jwtClaimsToVerify` - a list of (additional) JWT claims that must be present in the JWT token sent in `Authorization` header
    of the incoming webhook request which must be verified against webhook payload before accepting
     the webhook payload. Keys are the JWT claim names, and value are list of places to check against that claims value
          - eg, `queryParam`, `payloadContent`, `pathParam`, etc

```yaml
jwtClaimsToVerify:
    sub:
        queryParam: "userId"
        payloadContent: "$.user_id"
        pathParam: "userId"
endpoints:
    - jwtClaimsToVerify:
        sub:
            queryParam: "userId"
            payloadContent: "$.user_id"
            pathParam: "userId"
      transforms:
      - !<pseudonymize>
         jsonPaths:
           - "$.employeeEmail"
           - "$.managerEmail"
```

Terraform - `gcp-webhook-collector` and `aws-webhook-collector` modules that will deploy the necessary infrastructure. These will:
   - provision `-sanitized-webhooks` bucket for bulk storage of webhook payloads, readable to caller IAM role/principal (e.g. Worklytics tenant)

Webhooks will always be written as NDJSON (newline-delimited JSON) to the output bucket; gzipped.

To do this efficiently, we'd split into 2 steps: 1) webhook collector that receives webhook payload, accepts + sanitizes it, and then sends it to SQS. Then separately a trigger that 2) batches messages from SQS and writes them to the output bucket as NDJSON files.

https://docs.aws.amazon.com/lambda/latest/dg/services-sqs-configure.html

To handle both in same lambda, we need `WebhookCollectionModeHandler` to handle streams, and parse whether those are direct invocations of the webhook collector or SQS message batches

if webhook, then logic will:
- verify the JWT identity token in the `Authorization` header
- apply the transforms to the payload
- write the sanitized payload to SQS

if batch, then logic will:
- read message(s) from SQS
- batch them into NDJSON files
- write the NDJSON files to the output bucket

#### GCP

PubSub/Cloud Tasks doesn't natively support batching messages. The webhook collection cloud function instance is instead triggered via Cloud Scheduler (eg, every 5 minutes) to pull messages in batches from a PubSub topic.


### Future

#### Support Issuing Identity Tokens

1. Global, long-lived token

Allow some principal to sign tokens with auth key, nodejs script. Put resulting JWT in the app/script that sends payloads. OK if re-used by many clients.

2. User-specific, long-lived tokens

Allow some principal to sign tokens with the auth key, run nodejs script on a CSV of user identities to sign.

3. Global, short-lived token


4. User-specific, short-lived token

Allow your server's principal to sign tokens with auth key, run code like the following on each identity


TODO:
  - terraform support for passing in authorized signers (beyond just testers)
  - nodejs tooling to sign a CSV
  - endpoint in function/lambda that can be invoked by authorized signer, to get a signature??


#### Full Request Case

Use cases:
  - JIRA / Atlassian:JIRA webhooks include PII in the path/query string. How to deal with this??
      - `queryString` transforms, expecting json path stuff. [what if param included multiple times?]

 1. `collectionFormat` flag on `WebhookEndpoint`; enum of `PAYLOAD` (default) or `REQUEST`; in latter case, all json paths are relative to doc,
 2. `WebhookRequest` DTO; in REQUEST case, transform body of request to that and send whole thing in.


#### Authentication beyond Tokens
Additional authentication checks:
- IP range of request
- VPC - lock collectors to ONLY being reachable from specific VPC(s)

#### Collect Originals
Optionally, an `OUTPUT_ORIGINAL` environment variable can be set to store the original payloads in a different bucket/location.

#### Add FILTERS
- `method` - HTTP method (GET, POST, etc) that the rule applies to [ do we care?? maybe security to lock to `POST` if/when possible]
- `pathTemplate` - a path template that matches the incoming webhook request path. `null` means all paths are accepted.
- `pathParamFilters` - if defined, request must pass these path parameter filters to be accepted.
- `queryParamFilters` - if defined, request must pass  these query parameter filters to be accepted.
- `format` - `PAYLOAD`, `REQUEST` - only support `payload` for now, which means the payload is stored as-is.
- `filters` - a list of filters to apply to the incoming webhook payload before storing it; `JSONSchemaFilter` implementation.

Add a `REQUEST` format for writing webhooks --> storage:
```jsonc
{
    "timeReceived": "2023-10-01T12:00:00Z",
    "path": "/webhook/path",
    "method": "POST",
    "headers": [ // - is there EVER a need to store headers?
      {
        "name": "Header1",
        "value": "Value1",
      }
    ],
    "queryParameters": {
        "param1": "value1",
        "param2": "value2"
    },
    "payload": {
        "key1": "value1",
        "key2": "value2"
    }
}
```

### Issues
  - JIRA webhooks can mix a bunch of events of different schemas via single callback
  - need filters of some kind? (eg, avoid storage/processing of webhook payloads that don't have expected schemas?)
  - routing based on `method`, `path`, `queryParameters`?
