import test from 'ava';
import * as td from 'testdouble';

test.beforeEach(async t => {
  t.context.utils = await td.replaceEsm('../lib/utils.js'); 
  t.context.subject = (await import('../lib/gcp.js')).default;
});

test.afterEach(() => td.reset());

test('isValidURL URL', t => {
  const gcp = t.context.subject;

  t.true(gcp.isValidURL('https://foo.cloudfunctions.net'));
  t.true(gcp.isValidURL(new URL('https://foo.cloudfunctions.net')));

  t.false(gcp.isValidURL('http://foo.com'));
  t.throws(() => { gcp.isValidURL('foo'); }, {instanceOf: TypeError});
});

test('Psoxy Call: missing auth token throws error', async t => {
  const gcp = t.context.subject;

  const url = new URL('http://foo.com');
  await t.throwsAsync(async () => gcp.call(url, {}), {
    instanceOf: Error, 
    message: 'Authorization token is required for GCP endpoints',
  });
});
