# Async API Data Requests

For certain scenarios, you may want to orchestrate data collection via a REST API that is slow/has a large response time, without blocking for the response / processing it synchronously.

To support this, the proxy provides a `Process-Async` header which can be sent with API data requests; if header is sent, and async processing is enabled for the connector, the proxy will return a `202 Accepted` response immediately, and will process the request in the background, writing the response to the configured async output destination (usually a bucket).

## Configuration

To enable this, as `enable_async_processing` in the connector specs module.

Alternatively, to directly enable an instance, fill `ASYNC_OUTPUT_DESTINATION` environment variable with the destination for async outputs. Currently, only a bucket destination supported by the host platform is supported (eg, for AWS, this would be `s3://bucket-name/`).

Note that this is similar to side outputs, but distinct.  Async processing will be performed AS REQUESTED only; unless proxy client explicitly sends the header, the request will be process synchronously and the santized API respond directly return. If a side output is configured, both sync AND async processed requests will be written to the side output destination(s).




