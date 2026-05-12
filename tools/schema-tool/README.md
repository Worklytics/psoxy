# Schema Tool

Node.js CLI tool that infers the [JSON Schema] of an HTTP API endpoint response.

Given an endpoint URL and a Bearer token, it makes a GET request and prints the inferred schema of the response body alongside the response headers. Useful for exploring unfamiliar APIs and documenting their response shapes.

Requirements: [Node.js] (version >=18) and [npm]. Install dependencies first:

```shell
npm i
```

## Usage

```shell
node cli-schema.js -e <url> -a <token> [options]
```

| Option | Description |
|---|---|
| `-e, --endpoint <url>` | Endpoint URL to call (required) |
| `-a, --auth <token>` | Bearer token for authentication (required) |
| `--skip-headers` | Exclude response headers from output |
| `--raw` | Print raw response body instead of inferred schema |
| `-v, --verbose` | Print response status and headers to stderr |

## Output

By default the output is a JSON object with two keys: `headers` (the raw response headers) and `schema` (the inferred JSON Schema of the response body).

```shell
node cli-schema.js -e https://app.asana.com/api/1.0/workspaces -a $TOKEN
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
          },
          "required": { "type": "array", "items": { "type": "string" } }
        }
      }
    },
    "required": { "type": "array", "items": { "type": "string" } }
  }
}
```

The `required` field is itself described as a typed schema (an array of strings) rather than expanded inline, keeping the output consistent — every value is a JSON Schema type.

Use `--skip-headers` to omit the headers section when only the schema is needed:

```shell
node cli-schema.js -e https://app.asana.com/api/1.0/workspaces -a $TOKEN --skip-headers
```

Use `--raw` to print the unparsed response body, bypassing schema inference:

```shell
node cli-schema.js -e https://app.asana.com/api/1.0/workspaces -a $TOKEN --raw
```

## Notes

- Schema inference uses [`@jsonhero/schema-infer`] (JSON Schema 2020-12). It detects common string formats such as `date-time`, `date`, `uuid`, `email`, `uri`, and `hostname`.
- Fields present in only some array items are marked as optional (absent from `required`).
- Fields that are `null` in some items and a concrete type in others produce a union type (e.g. `"type": ["string", "null"]`).
- Non-2xx responses print the status and body to stderr and exit with code 1.

[JSON Schema]: https://json-schema.org
[Node.js]: https://nodejs.org/en/
[npm]: https://www.npmjs.com
[`@jsonhero/schema-infer`]: https://github.com/jsonhero-io/schema-infer
