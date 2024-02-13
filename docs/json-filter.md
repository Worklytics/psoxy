# JSON Filter

JSON Filter is inspired by [JSON Schema](https://json-schema.org/), but with the goal to **filter**
documents rather than **validate**. As such, the basic idea is that data nodes that do not match the
filter schema are removed, rather than the whole document failing validation.

The goal of JsonFilter is that only data elements specified in the filter pass through.

Some differences:

- `required` properties are ignored. While in JSON schema, an object that was missing a "required"
  property is invalid, objects missing "required" properties in a filter will be preserved.
- `{ }` , eg, a schema without a type, is interpreted as any valid leaf node (eg, unconstrained
  leaf; everything that's not 'array' or 'object') - rather than any valid JSON.

Compatibility goals:

- a valid JSON Schema is convertible to valid JSON filter (with JSON schema features not supported
  by JSON filter ignored)

## Motivations

### `{ }` as "any valid leaf node"

1. compactness, esp when encoding filter as YAML. can put `{ }` instead of `{ "type": "string" }`
2. flexibility; for filtering use-case, often you just care about which properties are/aren't
   passed, rather than 'string' vs 'number' vs 'integer'
3. "any valid JSON" is more common use case in validation than in filtering.
