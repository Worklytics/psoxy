# Side Outputs

*alpha* - this may change in backwards-incompatible ways in the future. it has not be widely used in production scenarios yet.

The **Side Outputs** feature allows you to configure additional outputs from your connector, to fulfill a couple of use cases:
  - desired content is too large/long to be processed in a single synchronous request from client --> proxy --> source and back
  - target API has rate limits; and you have another use for the desired response content
  - having extra copy of all data coming through the proxy, pre- or post-processing

If a **side output** is configured for an instance, a copy of each API response will be written to the side output. By default, this
will be the copy of the content AFTER it has been processed by the proxy, but you can also configure it to be the original content
exactly as returned by the source API.

By default, the response body will also be returned to the client (caller) of the proxy. But if client (caller) sends a
`X-Psoxy-No-Response-Body` header with request, then the proxy will not send a response body back to the client. Instead, it will
return just the status code / headers back to the client.

<!-- TODO : add data flow diagram of this case here -->


## Configuration

Use the following configuration properties to configure the side outputs from a proxy instance in API connector mode. These should
be set as environment variables in the proxy instance:
  - `SIDE_OUTPUT` - defines the target of the side output. Eg `s3://bucket-name/`, `gs://bucket-name/`, etc;
      - in the future, we might support `bq://dataset.table`, etc.
  - `SIDE_OUTPUT_CONTENT` - defines the format of the side output.
      - `ORIGINAL` - the original content from the source API
      - `SANITIZED` - the content after it has been processed by the proxy

Only a single side output is supported, at least for now.

## Details
For bucket use-cases, each response will be written as a single object in the bucket, with the following naming convention:

`GET_api.google.com/v1/some/endpoint_{SHA256(query-params)}`

These components are:
  - `GET` - the HTTP method used for the request
  - `api.google.com` - the host
  - `/v1/some/endpoint` the path of the API endpoint that was called (normalized)
  - `{SHA256(query-params)}` - a SHA256 hash of the (normalized) query parameters used in the request, to ensure uniqueness of the object name

See `SideOutputUtils::canonicalResponseKey` for details.


## Future Work
  - support for multiple side outputs
  - support for other targets (BQ, https endpoint, cloud watch, etc)
  - as a caching solution
  - as a buffer for slow/large responses (eg, proxy responds with token, which client can later use to fetch the data)
  - export `SideOutputUtils::canonicalResponseKey` to other languages; or document a standard with Java as reference implementation,
     so side output buckets can be used as a cache for API access by other systems.


