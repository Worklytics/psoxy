# Control Headers

API Data Connector mode accepts optional `X-Psoxy-*` **control headers**. These are consumed by the proxy itself (they are not forwarded to the source API) and steer how a request is interpreted.

Anything sent as a control header should not carry secrets or other high-sensitivity material beyond what you would already put in the request URL.

## Target path and query overrides

When a caller cannot put the intended source path or query string on the HTTP request itself (for example, because of a load balancer, API gateway, or other fronting layer that rewrites or constrains the URL), it may send:

| Header | Value | Effect |
| --- | --- | --- |
| `X-Psoxy-TargetPath` | Absolute path, e.g. `/v2/users/%7Bid%7D` | Used as the request path for rule matching and upstream URL construction. The actual HTTP request path is ignored. |
| `X-Psoxy-TargetQuery` | Raw query string **without** a leading `?`, e.g. `status=active&page=1` | Used as the request query string for rule matching and upstream URL construction. Any actual query string on the request is ignored. An empty value means “no query string”. |

Values are used **as-is** (no base64 or other encoding round-trip). Percent-encoded path segments should already be printable ASCII and can be sent directly.

### Validation

Both headers are treated as untrusted client input. Invalid values cause the proxy to respond with **HTTP 400** and `X-Psoxy-Error: INVALID_REQUEST`.

- `X-Psoxy-TargetPath` must start with `/`
- Neither header may contain CR/LF (header-injection defense), whitespace, or `#`
- `X-Psoxy-TargetPath` must not contain `?` (use `X-Psoxy-TargetQuery` for the query string)
- Length is capped (4 KB)
- Printable ASCII only (`0x20`–`0x7E`; spaces are still rejected by the whitespace rule)

When a valid `X-Psoxy-TargetPath` (or TargetQuery) is applied, the proxy logs the effective path/query and notes that it came from the control header (including the original request path/query for comparison).

### Interaction with path-prefix trimming

If the deployment also configures inbound path-prefix trimming (e.g. `REQUEST_PATH_PREFIX_TO_TRIM` for an ALB or gateway path prefix), a present `X-Psoxy-TargetPath` is the logical path for rules and upstream calls and should be used **exactly** — not run through prefix stripping. Prefer TargetPath when the client already knows the source API path; use path-prefix trimming when the HTTP path itself still includes a routing prefix that must be removed.

## Other control headers

| Header | Purpose |
| --- | --- |
| `X-Psoxy-Health-Check` | When present (any value), treated as a health check; not forwarded to the source. |
| `X-Psoxy-User-To-Impersonate` | User to impersonate when calling the source API (e.g. Google Workspace). |
| `X-Psoxy-Pseudonym-Implementation` | Selects pseudonym encoding implementation for the response. |
| `X-Psoxy-Skip-Sanitizer` | Skip sanitization when also enabled via proxy configuration (testing only). |
| `X-Psoxy-No-Response-Body` | Ask the proxy not to return a response body (useful with side outputs). See [Side Outputs](side-outputs.md). |
