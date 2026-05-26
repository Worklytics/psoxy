# Schema Tool

Node.js CLI tool that infers the [JSON Schema] of an HTTP API endpoint response — or of a local JSON/JSONL file.

Given an endpoint URL and a Bearer token it makes a GET request and prints the inferred schema of the response body alongside the response headers. Alternatively, pass a local file (or pipe JSON to stdin) to infer a schema without making any network request. Useful for exploring unfamiliar APIs and documenting their response shapes.

Requirements: [Node.js] (version >=18) and [npm]. Install dependencies first:

```shell
npm i
```

## Usage

### Endpoint mode

```shell
node cli-schema.js -e "<url>" -a <token> [options]
```

> **Important:** always quote URLs that contain query parameters (`?key=value`) to
> prevent the shell from interpreting `?` as a glob wildcard.

| Option | Description |
|---|---|
| `-e, --endpoint <url>` | Endpoint URL to fetch (required in this mode) |
| `-a, --auth <token>` | Bearer token for authentication (required in this mode) |
| `--skip-headers` | Exclude response headers from output |
| `--raw` | Print raw response body instead of inferring a schema |
| `-v, --verbose` | Print response status and headers to stderr |

### Input mode

```shell
node cli-schema.js -i <file|json|-> [--raw]
```

Infers the schema from local content without making any HTTP request. `--auth` is not needed. The value passed to `--input` is resolved in this order:

1. **`-`** — read from stdin
2. **file path** — read the file if it exists
3. **inline JSON/JSONL string** — treat the value itself as content

| Option | Description |
|---|---|
| `-i, --input <value>` | File path, inline JSON/JSONL string, or `-` for stdin |
| `--raw` | Print the raw content as-is instead of inferring a schema |

`--endpoint` and `--input` are mutually exclusive.

## Output

### Endpoint mode

By default the output is a JSON object with two keys: `headers` (the raw response headers) and `schema` (the inferred JSON Schema of the response body).

```shell
node cli-schema.js -e "https://app.asana.com/api/1.0/workspaces" -a $TOKEN
```

```json
{
  "headers": {
    "content-type": "application/json; charset=UTF-8",
    "x-request-id": "..."
  },
  "schema": {
    "type": "object",
    "properties": {
      "data": {
        "type": "array",
        "items": {
          "type": "object",
          "properties": {
            "gid":           { "type": "string" },
            "resource_type": { "type": "string" },
            "name":          { "type": "string" }
          }
        }
      }
    }
  }
}
```

Use `--skip-headers` to omit the headers section when only the schema is needed:

```shell
node cli-schema.js -e "https://app.asana.com/api/1.0/workspaces" -a $TOKEN --skip-headers
```

Use `--raw` to print the unparsed response body, bypassing schema inference:

```shell
node cli-schema.js -e "https://app.asana.com/api/1.0/workspaces" -a $TOKEN --raw
```

Query parameters are passed through correctly:

```shell
node cli-schema.js -e "https://app.asana.com/api/1.0/workspaces?limit=10" -a $TOKEN
```

### Input mode

The output contains only a `schema` key — there are no response headers because no HTTP request was made.

```shell
node cli-schema.js --input response.json
```

```json
{
  "schema": {
    "type": "object",
    "properties": {
      "id":   { "type": "integer" },
      "name": { "type": "string" }
    }
  }
}
```

Pass an inline JSON string directly without creating a file:

```shell
node cli-schema.js -i '{"data":[{"gid":"1234"}]}'
```

```json
{
  "schema": {
    "type": "object",
    "properties": {
      "data": {
        "type": "array",
        "items": {
          "type": "object",
          "properties": {
            "gid": {
              "type": "string",
              "format": "time"
            }
          }
        }
      }
    }
  }
}
```

> On shells that expand special characters (zsh, bash) use single quotes around
> the JSON string. If you must use double quotes, escape the inner quotes:
> `node cli-schema.js -i "{\"data\":[{\"gid\":\"1234\"}]}"`.

Read from stdin by passing `-`:

```shell
cat response.json | node cli-schema.js --input -
```

Pass `--raw` to print the content unchanged (works with files, inline strings, and stdin):

```shell
node cli-schema.js --input response.json --raw
```

## JSONL support

Endpoints and files that use newline-delimited JSON (one value per line) are detected automatically. When standard JSON parsing fails, the tool falls back to JSONL mode: each non-empty line is parsed individually and the resulting array is used for inference.

```shell
# From an endpoint
node cli-schema.js -e "https://api.example.com/v1/audit-log" -a $TOKEN --skip-headers

# From a local file
node cli-schema.js --input audit-log.jsonl
```

The inferred schema will have `"type": "array"` at the top level, with `items` describing the shape of a single log line.

## Error handling

Non-2xx responses always print the HTTP status and all response headers to stderr, followed by the response body when one is present, then exit with code 1. This is particularly useful for:

- **3xx redirects** — the `Location` header and a hint to re-run with the redirect URL are shown.
- **401 Unauthorized** — the `WWW-Authenticate` header is shown.
- **429 Too Many Requests** — the `Retry-After` header is shown.

## Notes

- Schema inference uses [`@jsonhero/schema-infer`] (JSON Schema 2020-12). It detects common string formats such as `date-time`, `date`, `uuid`, `email`, `uri`, `ipv4`, and `hostname`.
- Fields that are `null` in some items and a concrete type in others produce a union type (e.g. `"type": ["string", "null"]`).
- The inferred `required` list is stripped from the output because it reflects only what appeared in the sample payload, not the true API contract.

[JSON Schema]: https://json-schema.org
[Node.js]: https://nodejs.org/en/
[npm]: https://www.npmjs.com
[`@jsonhero/schema-infer`]: https://github.com/jsonhero-io/schema-infer
