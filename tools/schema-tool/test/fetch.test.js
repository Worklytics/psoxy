import test from 'ava';
import { EventEmitter } from 'events';
import * as td from 'testdouble';

// td.replaceEsm('https') must live in its own file — calling it in the same
// file that imports @jsonhero/schema-infer (a CJS dep chain) causes testdouble
// to throw on named export stubs.

test.serial.beforeEach(async (t) => {
  const fakeHttps = await td.replaceEsm('https');
  const { fetchEndpoint } = await import('../lib/schema.js');
  t.context.fetchEndpoint = fetchEndpoint;
  t.context.get = fakeHttps.default.get;
});

test.serial.afterEach(() => td.reset());

// fetchEndpoint now calls transport.get(requestOptions, callback) — two args only.
// requestOptions = { hostname, path, headers, timeout, [port] }
function stubGet(get, { statusCode = 200, statusMessage = 'OK', headers = {}, body = '' } = {}) {
  const res = new EventEmitter();
  res.statusCode = statusCode;
  res.statusMessage = statusMessage;
  res.headers = headers;

  const req = new EventEmitter();
  req.destroy = td.func('req.destroy');

  td.when(get(td.matchers.anything(), td.matchers.isA(Function)))
    .thenDo((_opts, callback) => {
      callback(res);
      setImmediate(() => {
        res.emit('data', body);
        res.emit('end');
      });
      return req;
    });

  return req;
}

test.serial('fetchEndpoint: 2xx — resolves with status, headers, and body', async (t) => {
  const headers = { 'content-type': 'application/json', 'x-request-id': 'abc123' };
  stubGet(t.context.get, { statusCode: 200, statusMessage: 'OK', headers, body: '{"ok":true}' });

  const result = await t.context.fetchEndpoint(new URL('https://api.example.com/v1'), 'token');

  t.is(result.status, 200);
  t.is(result.statusMessage, 'OK');
  t.deepEqual(result.headers, headers);
  t.is(result.body, '{"ok":true}');
});

test.serial('fetchEndpoint: non-2xx — resolves (does not reject) with error status and headers', async (t) => {
  const headers = { 'content-type': 'application/json', 'www-authenticate': 'Bearer' };
  stubGet(t.context.get, { statusCode: 401, statusMessage: 'Unauthorized', headers, body: '{"error":"unauthorized"}' });

  const result = await t.context.fetchEndpoint(new URL('https://api.example.com/v1'), 'bad-token');

  t.is(result.status, 401);
  t.is(result.statusMessage, 'Unauthorized');
  t.deepEqual(result.headers, headers);
  t.is(result.body, '{"error":"unauthorized"}');
});

test.serial('fetchEndpoint: network error — rejects with the error', async (t) => {
  const req = new EventEmitter();
  req.destroy = td.func('req.destroy');

  td.when(t.context.get(td.matchers.anything(), td.matchers.isA(Function)))
    .thenDo((_opts, _callback) => {
      setImmediate(() => req.emit('error', new Error('ECONNREFUSED')));
      return req;
    });

  await t.throwsAsync(
    () => t.context.fetchEndpoint(new URL('https://api.example.com/v1'), 'token'),
    { message: 'ECONNREFUSED' }
  );
});

// ── Query-parameter tests ──────────────────────────────────────────────────

test.serial('fetchEndpoint: query params — included in request path', async (t) => {
  stubGet(t.context.get, { statusCode: 200, body: '{"ok":true}' });

  await t.context.fetchEndpoint(
    new URL('https://api.example.com/v1/items?limit=10&offset=0'),
    'token'
  );

  const [opts] = td.explain(t.context.get).calls[0].args;
  t.is(opts.path, '/v1/items?limit=10&offset=0');
});

test.serial('fetchEndpoint: no query params — path has no trailing question mark', async (t) => {
  stubGet(t.context.get, { statusCode: 200, body: '{"ok":true}' });

  await t.context.fetchEndpoint(
    new URL('https://api.example.com/v1/items'),
    'token'
  );

  const [opts] = td.explain(t.context.get).calls[0].args;
  t.is(opts.path, '/v1/items');
  t.false(opts.path.includes('?'));
});

test.serial('fetchEndpoint: query params — hostname is set correctly', async (t) => {
  stubGet(t.context.get, { statusCode: 200, body: '{}' });

  await t.context.fetchEndpoint(
    new URL('https://api.example.com/v1/data?foo=bar'),
    'token'
  );

  const [opts] = td.explain(t.context.get).calls[0].args;
  t.is(opts.hostname, 'api.example.com');
  t.is(opts.path, '/v1/data?foo=bar');
});

test.serial('fetchEndpoint: explicit port in URL — forwarded to request options', async (t) => {
  stubGet(t.context.get, { statusCode: 200, body: '{}' });

  await t.context.fetchEndpoint(
    new URL('https://api.example.com:8443/v1/data'),
    'token'
  );

  const [opts] = td.explain(t.context.get).calls[0].args;
  t.is(opts.port, 8443);
});

test.serial('fetchEndpoint: default port (no port in URL) — port not set in options', async (t) => {
  stubGet(t.context.get, { statusCode: 200, body: '{}' });

  await t.context.fetchEndpoint(
    new URL('https://api.example.com/v1/data'),
    'token'
  );

  const [opts] = td.explain(t.context.get).calls[0].args;
  t.is(opts.port, undefined);
});

// ── 307 / redirect tests ───────────────────────────────────────────────────

test.serial('fetchEndpoint: 307 — resolves (does not reject) with status, location header, and body', async (t) => {
  const headers = {
    'location': 'https://api.example.com/v2/items',
    'content-length': '0',
  };
  stubGet(t.context.get, {
    statusCode: 307,
    statusMessage: 'Temporary Redirect',
    headers,
    body: '',
  });

  const result = await t.context.fetchEndpoint(
    new URL('https://api.example.com/v1/items'),
    'token'
  );

  t.is(result.status, 307);
  t.is(result.statusMessage, 'Temporary Redirect');
  // Location header must survive so callers can diagnose the redirect
  t.is(result.headers['location'], 'https://api.example.com/v2/items');
  t.is(result.body, '');
});

test.serial('fetchEndpoint: 301 — resolves with redirect status and location header', async (t) => {
  const headers = { 'location': 'https://new.example.com/v1/items' };
  stubGet(t.context.get, {
    statusCode: 301,
    statusMessage: 'Moved Permanently',
    headers,
    body: '',
  });

  const result = await t.context.fetchEndpoint(
    new URL('https://api.example.com/v1/items'),
    'token'
  );

  t.is(result.status, 301);
  t.is(result.headers['location'], 'https://new.example.com/v1/items');
});
