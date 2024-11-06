# HTTP Compression

## Overview

Given nature of use-case, proxy does A LOT of network transit:

- client to proxy (request)
- proxy to data source (request)
- data source to proxy (response)
- proxy to client (response)

So this drives cost in several ways:

- larger network payloads increases proxy running time, which is billable
- network volume itself is billable in some host platforms
- indirectly, clients are waiting for proxy to respond, so that's an indirect cost (paid on
  client-side)

Generally, proxy is transferring JSON data, which is highly compressible. Using `gzip` likely to
reduce network volume by 50-80%. So we want to make sure we do this everywhere.

As of Aug 2023, we're not bothering with compressing requests, as expected to be small (eg, current
proxy use-cases don't involve large `PUT` / `POST` operations).

## Proxy-to-Client Response

### AWS

#### Function Urls

Compression must be managed at the application layer (eg, in our proxy code).

This is done in `co.worklytics.psoxy.Handler`, which uses `ResponseCompressionHandler` to detect
request for compressed response, and then compress the response.

#### API Gateway

API Gateway is no longer used by our default terraform examples. But compression can be enabled at
the gateway level (rather than relying on function url implementation, or in addition to).

https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-gzip-compression-decompression.html

### GCP

GCP Cloud Functions will handle compression themselves IF the request meets various conditions.

There is no explicit, Cloud Function-specific documentation about this, but it seems that the
behavior for App Engine applies:

https://cloud.google.com/appengine/docs/legacy/standard/go111/how-requests-are-handled#:~:text=For%20responses%20that%20are%20returned,HTML%2C%20CSS%2C%20or%20JavaScript.

## Source-to-Proxy Response

All requests should be built using `GzipedContentHttpRequestInitializer`, which should add:

- `Accept-Encoding: gzip`
- append `(gzip)` to `User-Agent` header

We believe this will trigger compression for most sources (the User-Agent thing being practice that
Google seems to want).
