import test from 'ava';
import * as td from 'testdouble';
import _ from 'lodash';
import { constants as httpCodes } from 'http2';
import { createRequire } from 'module';
const require = createRequire(import.meta.url);
const logsSample = require('./gcp-log-entries-structured-sample.json');

const GCP_URL = 'https://foo.cloudfunctions.net';

test.beforeEach(async (t) => {
  t.context.utils = await td.replaceEsm('../lib/utils.js');
  t.context.subject = (await import('../lib/gcp.js')).default;
});

test.afterEach(() => td.reset());

test('isValidURL URL', (t) => {
  const gcp = t.context.subject;

  t.true(gcp.isValidURL(GCP_URL));
  t.false(gcp.isValidURL('http://foo.com'));

  t.throws(
    () => {
      gcp.isValidURL('foo');
    },
    { instanceOf: TypeError }
  );
});

test('Get logs link based on cloud function URL', (t) => {
  const gcp = t.context.subject;

  t.is(undefined, gcp.getLogsURL('foo'));
  t.is('https://console.cloud.google.com/functions/details/us-central1/psoxy-function?project=psoxy-project&tab=logs',
    gcp.getLogsURL('https://us-central1-psoxy-project.cloudfunctions.net/psoxy-function'))
})

test('Psoxy Logs: parse log entries', (t) => {
  const gcp = t.context.subject;

  t.deepEqual(gcp.parseLogEntries(), []);

  const result = gcp.parseLogEntries(logsSample);

  t.truthy(result[0].timestamp, 'Contains timestamp');
  t.truthy(result[0].message, 'Contains message');

  const errorSeverity = 'ERROR';
  const errorEntryIndex = logsSample
    .findIndex(entry => entry.severity === 'ERROR');

  t.is(result[errorEntryIndex].level, errorSeverity);

  // it handles one level of nesting in messsages
  const nestedMessageEntryIndex = logsSample
    .findIndex(entry => _.isObject(entry.message));
  t.true(_.isString(result[nestedMessageEntryIndex].message))
});

test('Psoxy Call: get identity token when option missing', async (t) => {
  const gcp = t.context.subject;
  const utils = t.context.utils;

  const options = {
    url: GCP_URL,
    method: 'GET',
  };

  const COMMAND = 'gcloud auth print-identity-token';
  const TOKEN = 'foo';
  td.when(utils.executeCommand(COMMAND)).thenReturn(TOKEN);
  td.when(
    utils.request(
      td.matchers.isA(URL),
      td.matchers.contains('GET'),
      td.matchers.contains({
        Authorization: `Bearer ${TOKEN}`,
      }),
      td.matchers.anything()
    )
  ).thenReturn({ status: httpCodes.HTTP_STATUS_OK });

  const result = await gcp.call(options);
  t.is(result.status, httpCodes.HTTP_STATUS_OK);
});
