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

function stubGet(get, { statusCode = 200, statusMessage = 'OK', headers = {}, body = '' } = {}) {
  const res = new EventEmitter();
  res.statusCode = statusCode;
  res.statusMessage = statusMessage;
  res.headers = headers;

  const req = new EventEmitter();
  req.destroy = td.func('req.destroy');

  td.when(get(td.matchers.anything(), td.matchers.anything(), td.matchers.isA(Function)))
    .thenDo((_url, _opts, callback) => {
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

  td.when(t.context.get(td.matchers.anything(), td.matchers.anything(), td.matchers.isA(Function)))
    .thenDo((_url, _opts, _callback) => {
      setImmediate(() => req.emit('error', new Error('ECONNREFUSED')));
      return req;
    });

  await t.throwsAsync(
    () => t.context.fetchEndpoint(new URL('https://api.example.com/v1'), 'token'),
    { message: 'ECONNREFUSED' }
  );
});
