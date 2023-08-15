# API Data Sanitization


Psoxy supports specifying sanitization rule sets to use to sanitize data from an API. These can be
configured by encoding a rule set in YAML and setting a parameter in your instance's configuration.

If such a parameter is not set, a proxy instances selects default rules based on source kind, from
the following supported sources found in [`sources/example-rules`](sources/example-rules).

You can configure custom rule sets for a given instance via Terraform, by adding an entry to the
`custom_api_connector_rules` map in your `terraform.tfvars` file.

eg,

```hcl
custom_api_connector_rules = {
    gmail: "custom-gmail.yaml"
}
```

## API Connector Rules Syntax

`<ruleset> ::= "endpoints:" <endpoint-list>`
`<endpoint-list> ::= <endpoint> | <endpoint> <endpoint-list>`

A ruleset is a list of API endpoints that are permitted to be invoked through the proxy.  Requests which do not match a
endpoint in this list will be rejected with a `403` response.

### Endpoint Specification
`<endpoint> ::= <path-template> <transforms>`

`<path-template> ::= "- pathTemplate: " <string>`
Each endpoint is specified by a path template, based on OpenAPI Spec v3.0.0 Path Template syntax.  Variable path
segments are enclosed in curly braces (`{}`) and are matched by any value that does not contain an `/` character.

See: https://swagger.io/docs/specification/paths-and-operations/

`<transforms> ::= "transforms:" <transform-list>`
`<transform-list> ::= <transform> | <transform> <transform-list>`

For each Endpoint, rules specify a list of transforms to apply to the response content.


### Transform Specification

`<transform> ::= "- " <transform-type> <json-paths> [<encoding>]`

Each transform is specified by a transform type and a list of [JSON paths](https://github.com/json-path/JsonPath). The
transform is applied to all portions of the response content that match any of the JSON paths.

Supported Transform Types:

`<transform-type> ::= "!<pseudonymizeEmailHeader>" | "!<pseudonymize>" | "!<redact>" | "!<redactRegexMatches>" | "!<tokenize>" | "<!filterTokenByRegex>" | "!<redactExceptSubstringsMatchingRegexes"`

NOTE: these are implementations of `com.avaulta.gateway.rules.transforms.Transform` class in the psoxy codebase.

#### Pseudonymize

`!<pseudonymize>` - transforms matching values by normalizing them (triming whitespace; if appear to
be emails, treating them as case-insensitive, etc) and computing a SHA-256 hash of the normalized
value.  Relies on `SALT` value configured in your proxy environment to ensure the SHA-256 is
deterministic across time and between sources.  In the case of emails, the domain portion is
preserved, although the hash is still based on the entire normalized value (avoids hash of
`alice@acme.com` matching hash of `alice@beta.com`).

Options:
   - `includeReversible` (default: `false`): If `true`, an encrypted form of the original value will
      be included in the result. This value, if passed back to the proxy in a URL, will be decrypted
      back to the original value before the request is forward to the data source. This is useful
      for identifying values that are needed as parameters for subsequent API requests.
      This relies on symmetric encryption using the `ENCRYPTION_KEY` secret stored in the proxy; if
      `ENCRYPTION_KEY` is rotated, any 'reversible' value previously generated will no longer be
      able to be decrypted by the proxy.
   - `encoding` (default: `JSON`): The encoding to use when serializing the pseudonym to a string.
        - `JSON` - a JSON object structure, with explicit fields
        - `URL_SAFE_TOKEN` - a string format that aims to be concise, URL-safe, and format-preserving
           for email case.

#### Pseudonymize Email Header

`!<pseudonymizeEmailHeader>` - transforms matching values by parsing the value as an email header,
in accordance with RFC 2822 and some typical conventions, and generating a pseudonym based only on
the normalized email address itself (ignoring name, etc that may appear) . In particular:
   - deals with CSV lists (multiple emails in a single header)
   - handles the `name <email>` format, in effect redacting the name and replacing with a pseudonym
     based only on normalized `email`


#### Redact

`!<redact>` - removes the matching values from the response.

Some extensions of redaction are also supported:
   - `!<redactExceptSubstringsMatchingRegexes>` - removes the matching values from the response
      except value matches one of the specified `regex` options. (Use case: preserving portions of
      event titles if match variants of 'Focus Time', 'No Meetings', etc)
   - `!<redactRegexMatches>` - redact content IF it matches one of the `regex`s included as an option.

#### Tokenize

`!<tokenize>` - replaces matching values it with a reversible token, which proxy can reverse to the
original value using `ENCRYPTION_KEY` secret stored in the proxy in subsequent requests.

Use case are values that *may* be sensitive, but are opaque. For example, page tokens in Microsoft
Graph API do not have a defined structure, but in practice contain PII.

Options:
   - `regex` a capturing regex to use to extract portion of value that needs to be tokenized.


#### Filter Tokens by Regex

`!<filterTokenByRegex>` - tokenizes matching string values by a delimiter, if provided; and matches
result against a list of `filters`, removing any content that doesn't match at least one of the
filters.
(Use case: preserving Zoom URLs in meeting descriptions, while removing the rest of the description)

Options:
  - `delimiter` - used to split the value into tokens; if not provided, the entire value is treated
     as a single token.
  - `filters` - in effect, combined via OR; tokens matching ANY of the filters is preserved in the
     value.




