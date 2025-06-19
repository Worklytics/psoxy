import test from 'ava';
import * as td from 'testdouble';
import { createRequire } from 'module';
import { constants as httpCodes } from 'http2';
const require = createRequire(import.meta.url);
const logsSample = require('./cloudwatch-log-events-sample.json').events;

const LAMBDA_URL = 'https://foo.lambda-url.us-east-1.on.aws/';
const API_GATEWAY_URL = 'https://foo.execute-api.us-east-1.amazonaws.com';
// Fake assume role and AWS request signing
const options = {
  url: LAMBDA_URL,
  role: 'arn:aws:iam::[accountId]:role/[roleName]',
  method: 'GET',
  region: 'us-east-1',
};
const credentials = {
  accessKeyId: 'foo',
  secretAccessKey: 'bar',
  sessionToken: 'baz',
};
const signedRequest = {
  headers: {
    Host: 'aws-host',
    'X-Amz-Security-Token': 'token',
    'X-Amz-Date': '20221014T172512Z',
    Authorization: 'AWS4-HMAC-SHA256 ...',
  },
  host: 'aws-host',
  path: '/foo',
  region: 'us-east-1',
  service: 'lambda',
};

test.beforeEach(async (t) => {
  t.context.utils = await td.replaceEsm('../lib/utils.js');
  t.context.subject = (await import('../lib/aws.js')).default;

  // mock credentials
  td.when(t.context.utils.getAWSCredentials(td.matchers.contains(options.role),
    td.matchers.contains(options.region)))
    .thenResolve(credentials);

  // mock request signing using credentials
  td.when(
    t.context.utils.signAWSRequestURL(
      td.matchers.isA(URL),
      td.matchers.argThat((arg) => ['GET', 'POST'].includes(arg)),
      td.matchers.anything(),
      credentials,
      options.region,
    )
  ).thenReturn(signedRequest);
});

test.afterEach(() => td.reset());

test('isValidURL URL', (t) => {
  const aws = t.context.subject;
  t.true(aws.isValidURL(LAMBDA_URL));
  t.true(aws.isValidURL(new URL(LAMBDA_URL)));
  t.true(aws.isValidURL(API_GATEWAY_URL));
  t.true(aws.isValidURL(new URL(API_GATEWAY_URL)));

  t.false(aws.isValidURL('http://foo.com'));
  t.throws(
    () => {
      aws.isValidURL('foo');
    },
    { instanceOf: TypeError }
  );
});

test('Psoxy Logs: parse log events command result', (t) => {
  const aws = t.context.subject;

  t.deepEqual([], aws.parseLogEvents(null));
  t.deepEqual([], aws.parseLogEvents({}));

  const result = aws.parseLogEvents(logsSample);

  t.is(result.length, logsSample.length);
  // It doesn't modify the message
  t.is(result[0].message, logsSample[0].message);

  // It formats the timestamp
  t.not(result[0].timestamp, logsSample[0].timestamp);

  // It creates a new property "level" if the starting of the message matches
  // "SEVERE" or "WARNING" logging Java levels, and removes
  // the level keyword from the original message
  const severePrefix = 'SEVERE';
  const severeEventIndex = logsSample
    .findIndex(event => event.message.startsWith(severePrefix));
  t.is(result[severeEventIndex].level, severePrefix);
  t.is(result[severeEventIndex].highlight, true);
  t.not(result[severeEventIndex].message.startsWith(severePrefix));

  const warningPrefix = 'WARNING';
  const warningEventIndex = logsSample
    .findIndex(event => event.message.startsWith(warningPrefix));
  t.is(result[warningEventIndex].level, warningPrefix);
  t.is(result[severeEventIndex].highlight, true);
  t.not(result[warningEventIndex].message.startsWith(warningPrefix));
});

test.serial('Psoxy call: works as expected', async (t) => {
  const aws = t.context.subject;
  const utils = t.context.utils;

  // Valid URL
  options.url += '/api/path';
  td.when(
    utils.request(
      td.matchers.isA(URL),
      td.matchers.contains('GET'),
      td.matchers.contains(signedRequest.headers),
      td.matchers.anything()
    )
  ).thenReturn({ status: httpCodes.HTTP_STATUS_OK });

  const result = await aws.call(options);

  t.is(result.status, httpCodes.HTTP_STATUS_OK);
});

test.serial('Psoxy call: pathless URL results 500', async (t) => {
  const aws = t.context.subject;
  const utils = t.context.utils;

  // Pathless URL: result should contain error
  td.when(
    utils.request(
      td.matchers.isA(URL),
      td.matchers.contains('GET'),
      td.matchers.contains(signedRequest.headers),
      td.matchers.anything(),
    )
  ).thenReturn({
    status: 500,
    headers: {
      'x-psoxy-error': 'BLOCKED_BY_RULES'
    }
  });

  const result = await aws.call(options);
  t.is(result.headers['x-psoxy-error'], 'BLOCKED_BY_RULES');
});

test.serial('Psoxy call: with POST, signingKey, and identityToSign options (Webhook use-case)',
  async(t) => {
    const aws = t.context.subject;
    const utils = t.context.utils;

    options.url += '/foo';
    options.method = 'POST';
    options.body = JSON.stringify({ foo: 'bar' });
    options.signingKey = 'aws-kms:foo';
    options.identityToSign = 'test-identity';

    const jwtSignatureExample = 'jwtSignatureExample';

    td.when(
      utils.signJwtWithKMS(
        td.matchers.contains({
          iss: `https://${new URL(options.url).hostname}`,
          sub: options.identityToSign,
          aud: options.url,
        }),
        td.matchers.contains('foo'), // signingKey without 'aws-kms:' prefix
        td.matchers.contains(credentials),
        td.matchers.contains(options.region),
      )
    ).thenReturn(jwtSignatureExample);

    td.when(
      utils.request(
        td.matchers.isA(URL),
        td.matchers.contains('POST'),
        td.matchers.contains({
          ...signedRequest.headers,
          // if signing works this header must be included in the request
          'x-psoxy-authorization': jwtSignatureExample,
        }),
        td.matchers.contains(options.body)
      )
    ).thenReturn({ status: httpCodes.HTTP_STATUS_OK });

    const result = await aws.call(options);

    t.is(result.status, httpCodes.HTTP_STATUS_OK);
  })
