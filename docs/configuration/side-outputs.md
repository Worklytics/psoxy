# Side Outputs

*alpha* - this may change in backwards-incompatible ways in the future. it has not be widely used in production scenarios yet.

The **Side Outputs** feature allows you to configure additional outputs from your connector, to fulfill a couple of use cases:
  - desired content is too large/long to be processed in a single synchronous request from client --> proxy --> source and back
  - target API has rate limits; and you have another use for the desired response content
  - having extra copy of all data coming through the proxy, pre- or post-processing

If a **side output** is configured for an instance, a copy of each API response will be written to the side output. By default, this will be the copy of the content AFTER it has been processed by the proxy, but you can also configure it to be the original content exactly as returned by the source API.

By default, the response body will also be returned to the client (caller) of the proxy. But if client (caller) sends a `X-Psoxy-No-Response-Body` header with request, then the proxy will not send a response body back to the client. Instead, it will return just the status code / headers back to the client.

<!-- TODO : add data flow diagram of this case here -->

## Configuration

Use the following configuration properties to configure the side outputs from a proxy instance in API connector mode. These should be set as environment variables in the proxy instance:
  - `SIDE_OUTPUT_ORIGINAL`/`SIDE_OUTPUT_SANITIZED` - defines the target of the side output. Eg `s3://bucket-name/`, `gs://bucket-name/`, etc;
      - in the future, we might support `bq://dataset.table`, etc.
      - `ORIGINAL`/`SANITIZED` - the **stage** of the data to be written to the side output.
        - `ORIGINAL` - the original response from the source API, before any processing by the proxy
        - `SANITIZED` - the response after it has been processed by the proxy (eg, sanitized, transformed, etc)

Setting this up in terraform:

Approaches:
    1. `custom_side_outputs` in gcp/aws host modules; add key ==> configuration to that variable.

Use case: you want a back-up of the data, or to have all the requests and responses otherwise available.

```hcl
custom_side_outputs = {
    "zoom" = {
        'ORIGINAL'  = "s3://my-bucket/original-outputs/"
        'SANITIZED' = "s3://my-bucket/side-outputs/"
    }
}
```
   2. connector specs. a `provision_side_output` in the connector spec, which will cause the bucket to be provisioned. **NOT YET IMPLEMENTED**.

Use case: some responses from source API expected to be too large/too slow to return directly to the client, so you want to write them to a side output instead.

```hcl
provision_side_output = true
```

## Details
For bucket use-cases, each response will be written as a single object in the bucket, with the following naming convention:

`api.google.com/v1_some_endpoint/{random-uuid}`

These components are:
  - `api.google.com` - the host
  - `v1_some_endpoint` the path of the API endpoint that was called (normalized, and replacing `/` with `_` to avoid semanticis of directory structure in bucket UX)
  - `{random-uuid}` - a random UUID, to ensure uniqueness of the object name.

Additionally, a bunch of metadata will be populated on the object, including:
- `API_HOST` - the host of the API from which the response was received
- `PATH` - the path that was called
- `HTTP_METHOD` - the HTTP method that was used to call the API
- `QUERY_STRING` - the query string that was used to call the API
- `REQUEST_BODY` - base64-encoded request body that was sent to the API, if any (eg, only applicable for `POST`/`PUT` requests)
- all http headers from the request to the proxy for the API data EXCEPT for
    - `host`
    - `user-agent`
    - `accept`
    - `accept-encoding`
    - `authorization`
    - `content-length`
    - `Forwarded`
    - `traceparent`
    - `X-Forwarded-For`
    - `x-cloud-trace-context`
    - `X-Forwarded-Proto`


### Passing between Terraform Modules

 - down to the per-instance modules:

`side_output` variable?

`side_outputs` - list of the above?

`side_output_sanitized`, `side_output_original` - one per stage?

## Issues
  - async mode WITHOUT side output; atm, can't have both; async implies side output, to which data is written regardless of context

  - headers that matter / don't matter in response content; what to do? (eg Google 'User-to-Impersonate') - that's a proprietary one.
  - do we need to store Response headers, as well as Request headers, in object metadata?
  - `/` in paths get split up in GCS/S3 ux, which is annoying; makes it hard to browse the bucket.
  - allow customization of side-output object naming?? can see how some query parameters/etc might make them more readable

## Future Work
  - support for multiple side outputs ?? ( probably unavoidable, and should do ASAP )
      - or tbh, combine them into a single bucket probably, right??  depends
  - support for other targets (BQ, https endpoint, cloud watch, etc)
  - as a caching solution
  - as a buffer for slow/large responses (eg, proxy responds with token, which client can later use to fetch the data)
  - support for additional transforms on side outputs? What's the use-case exactly?
  - support for side output sampling rate?
