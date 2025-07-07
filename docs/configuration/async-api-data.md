# Async API Data Requests

For certain scenarios, you may want to orchestrate data collection via a REST API that is slow/has a large response time, without blocking for the response / processing it synchronously.

To support this, send `Prefer: respond-async` header which can be sent with API data requests; if header is sent, and async processing is enabled for the connector, the proxy will return a `202 Accepted` response immediately, and will process the request in the background, writing the response to the configured async output destination (usually a bucket).

It will also respond with a `Location` header, which is the location of the async response. At the moment, this is a plain `s3://` URL, which is not standard compliant - as this is not `https://` URL, and also the client cannot necessarily access the bucket directly at that endpoint.



## Configuration

To enable this, as `enable_async_processing` in the connector specs module.

Alternatively, to directly enable an instance, fill `ASYNC_OUTPUT_DESTINATION` environment variable with the destination for async outputs. Currently, only a bucket destination supported by the host platform is supported (eg, for AWS, this would be `s3://bucket-name/`).

Note that this is similar to side outputs, but distinct.  Async processing will be performed AS REQUESTED only; unless proxy client explicitly sends the header, the request will be process synchronously and the santized API respond directly return. If a side output is configured, both sync AND async processed requests will be written to the side output destination(s).



## Issues

### Properly filling Location header

Options:
  1. presigned URL to the bucket, at https endpoint. This is standards compliant but 1) requires proxy to do round-trip to S3 for signed URL.  2) creates a lot of coupling between ApiDataRequestHandler and the host platform.
  2. return a URL to an endpoint in the proxy that will retrieve the file. This works but 1) creates requirement that proxy can read from the bucket (prob reasonable), 2) means that this is no longer really a 'proxy', unless we require client to send a control header.
  3. echo back the same request URL as the 'Location' value; if client sends that request WITHOUT the 'Prefer: respond-async' and async response is enabled, we check for s3 object FIRST before trying the API data source??


(3) seems clunky, suboptimal unless this async is the ONLY use case for the connector and ALL requests are long-running async ones, where overhead of checking for s3 object is not a problem.

If we ever do request changing, then any implementation of this doesn't exactly follow the standard ...





