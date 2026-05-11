import { inferSchema as _inferSchema } from '@jsonhero/schema-infer';
import http from 'http';
import https from 'https';

const REQUEST_TIMEOUT_MS = 25000;

/**
 * Infer a JSON Schema 2020-12 document from a parsed JSON value.
 * Delegates to @jsonhero/schema-infer which handles format detection
 * (date-time, uuid, email, uri), nullable union types, and required fields.
 *
 * @param {*} value
 * @returns {Object} JSON Schema 2020-12 object
 */
export function inferSchema(value) {
  return _inferSchema(value).toJSONSchema();
}

/**
 * Recursively convert `required` arrays in a JSON Schema into proper type
 * descriptions: `["a","b"]` → `{ type: "array", items: { type: "string" } }`.
 *
 * @jsonhero/schema-infer produces valid JSON Schema 2020-12, where `required`
 * is an array of raw property-name strings. This function normalises those
 * arrays so every value in the schema is itself described as a JSON Schema
 * type, making the output consistent.
 *
 * @param {Object} schema - JSON Schema object
 * @returns {Object}
 */
export function describeRequired(schema) {
  if (!schema || typeof schema !== 'object' || Array.isArray(schema)) return schema;

  const result = { ...schema };

  if (Array.isArray(result.required)) {
    result.required = { type: 'array', items: { type: 'string' } };
  }

  if (result.properties) {
    result.properties = Object.fromEntries(
      Object.entries(result.properties).map(([key, propSchema]) => [key, describeRequired(propSchema)])
    );
  }

  if (result.items && typeof result.items === 'object' && result.items !== false) {
    result.items = describeRequired(result.items);
  }

  return result;
}

/**
 * Fetch a URL with a Bearer token.
 *
 * @param {URL} url
 * @param {string} token
 * @returns {Promise<{status: number, statusMessage: string, headers: Object, body: string}>}
 */
export function fetchEndpoint(url, token) {
  const transport = url.protocol === 'https:' ? https : http;
  return new Promise((resolve, reject) => {
    const req = transport.get(url.toString(), {
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: 'application/json',
      },
      timeout: REQUEST_TIMEOUT_MS,
    }, (res) => {
      let body = '';
      res.on('data', (chunk) => (body += chunk));
      res.on('end', () =>
        resolve({
          status: res.statusCode,
          statusMessage: res.statusMessage,
          headers: res.headers,
          body,
        })
      );
    });
    req.on('error', reject);
    req.on('timeout', () => req.destroy(new Error('Request timeout')));
  });
}
