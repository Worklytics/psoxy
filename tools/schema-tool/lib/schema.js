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
 * Recursively strip `required` arrays from a JSON Schema.
 * The inferred `required` list reflects only what appeared in the sample
 * payload, not the true API contract, so including it in output is misleading.
 *
 * @param {Object} schema - JSON Schema object
 * @returns {Object}
 */
export function removeRequired(schema) {
  if (!schema || typeof schema !== 'object' || Array.isArray(schema)) return schema;

  const result = { ...schema };

  // Strip `required` only when it is the JSON Schema keyword (always an array).
  // A data field named `required` lives inside `properties` as a sub-schema
  // and is never an array, so it passes through untouched.
  if (Array.isArray(result.required)) delete result.required;

  if (result.properties) {
    result.properties = Object.fromEntries(
      Object.entries(result.properties).map(([key, propSchema]) => [key, removeRequired(propSchema)])
    );
  }

  if (result.items && typeof result.items === 'object' && result.items !== false) {
    result.items = removeRequired(result.items);
  }

  return result;
}

/**
 * Parse a response body that is either a single JSON value or JSONL
 * (one JSON value per line). JSONL is tried only when standard JSON.parse
 * fails, so a normal JSON array is never misidentified as JSONL.
 *
 * @param {string} body
 * @returns {*} parsed value — an array when the input is JSONL
 * @throws {SyntaxError} when the body is neither valid JSON nor valid JSONL
 */
export function parseBody(body) {
  try {
    return JSON.parse(body);
  } catch {
    const lines = body.split('\n').filter(l => l.trim() !== '');
    if (lines.length === 0) throw new SyntaxError('Empty response body');
    return lines.map(line => JSON.parse(line));
  }
}

/**
 * Fetch a URL with a Bearer token.
 *
 * Query parameters are explicitly forwarded via `path: url.pathname + url.search`
 * to avoid a Node.js quirk where passing a URL string + a separate options object
 * can cause the search string to be dropped when the two are merged internally.
 *
 * @param {URL} url
 * @param {string} token
 * @returns {Promise<{status: number, statusMessage: string, headers: Object, body: string}>}
 */
export function fetchEndpoint(url, token) {
  const transport = url.protocol === 'https:' ? https : http;

  // Build an explicit options object so that url.search (query string) is never
  // silently dropped. url.search is '' when there are no params and '?...' otherwise.
  const requestOptions = {
    hostname: url.hostname,
    path: url.pathname + url.search,
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/json',
    },
    timeout: REQUEST_TIMEOUT_MS,
  };
  // Only set port when the URL explicitly specifies one; otherwise let the
  // transport module use its default (80 for http, 443 for https).
  if (url.port) {
    requestOptions.port = parseInt(url.port, 10);
  }

  return new Promise((resolve, reject) => {
    const req = transport.get(requestOptions, (res) => {
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
