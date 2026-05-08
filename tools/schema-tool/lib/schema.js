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
