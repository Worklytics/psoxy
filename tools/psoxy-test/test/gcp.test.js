import test from 'ava';
import * as td from 'testdouble';

const GCP_URL = 'https://foo.cloudfunctions.net';

test.beforeEach(async t => {
  t.context.utils = await td.replaceEsm('../lib/utils.js'); 
  t.context.subject = (await import('../lib/gcp.js')).default;
});

test.afterEach(() => td.reset());

test('isValidURL URL', t => {
  const gcp = t.context.subject;

  t.true(gcp.isValidURL(GCP_URL));
  t.true(gcp.isValidURL(GCP_URL));

  t.false(gcp.isValidURL('http://foo.com'));
  t.throws(() => { gcp.isValidURL('foo'); }, {instanceOf: TypeError});
});

test('Psoxy Call: get identity token when option missing', async t => {
  const gcp = t.context.subject;
  const utils = t.context.utils;
  
  const options = {
    url: new URL(GCP_URL),
  }

  const COMMAND = 'gcloud auth print-identity-token';
  const TOKEN = 'foo';
  td.when(utils.executeCommand(COMMAND)).thenReturn(TOKEN);
  td.when(utils.request(options.url, td.matchers.contains({
    Authorization: `Bearer ${TOKEN}`
  }))).thenReturn({status: 200});

  const result = await gcp.call(options);
  t.is(result.status, 200);

  td.verify(utils.executeCommand(COMMAND));
});
