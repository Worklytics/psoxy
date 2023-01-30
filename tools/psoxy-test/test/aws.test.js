import test from 'ava';
import * as td from 'testdouble';
import { createRequire } from 'module';
import { constants as httpCodes } from 'http2';
const require = createRequire(import.meta.url);
const logsSample = require('./cloudwatch-log-events-sample.json').events;

const LAMBDA_URL = 'https://foo.lambda-url.us-east-1.on.aws/';

test.beforeEach(async (t) => {
  t.context.utils = await td.replaceEsm('../lib/utils.js');
  t.context.subject = (await import('../lib/aws.js')).default;
});

test.afterEach(() => td.reset());

test('isValidURL URL', (t) => {
  const aws = t.context.subject;
  t.true(aws.isValidURL(LAMBDA_URL));
  t.true(aws.isValidURL(new URL(LAMBDA_URL)));

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

test('Psoxy call: missing role throws error', async (t) => {
  const aws = t.context.subject;
  await t.throwsAsync(
    async () =>
      aws.call({
        url: LAMBDA_URL,
      }),
    {
      instanceOf: Error,
      message: 'Role is a required option for AWS',
    }
  );
});

test('Psoxy call: works as expected', async (t) => {
  const aws = t.context.subject;
  const utils = t.context.utils;

  // Fake assume role and AWS request signing
  const options = {
    url: LAMBDA_URL,
    role: 'arn:aws:iam::[accountId]:role/[roleName]',
    method: 'GET',
  };
  const credentials = {
    Credentials: {
      AccessKeyId: 'foo',
      SecretAccessKey: 'bar',
      SessionToken: 'baz',
    },
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

  // notice that `executeCommand` will return credentials wrapped and as
  // String; in contrast to `assumeRole` that will parse and unwrap
  td.when(utils.executeCommand(td.matchers.contains('aws sts assume-role')))
    .thenReturn(JSON.stringify(credentials));

  // sign request using credentials
  td.when(
    utils.signAWSRequestURL(
      td.matchers.isA(URL),
      td.matchers.contains('GET'),
      credentials.Credentials
    )
  ).thenReturn(signedRequest);

  // Pathless URL: result should contain error
  td.when(
    utils.request(
      td.matchers.isA(URL),
      td.matchers.contains('GET'),
      td.matchers.contains(signedRequest.headers)
    )
  ).thenReturn({
    status: 500,
    headers: {
      'x-psoxy-error': 'BLOCKED_BY_RULES'
    }
  });

  let result = await aws.call(options);
  t.is(result.headers['x-psoxy-error'], 'BLOCKED_BY_RULES');

  // Valid URL
  options.url += '/api/path';
  td.when(
    utils.request(
      td.matchers.isA(URL),
      td.matchers.contains('GET'),
      td.matchers.contains(signedRequest.headers)
    )
  ).thenReturn({ status: httpCodes.HTTP_STATUS_OK });

  result = await aws.call(options);
  t.is(result.status, httpCodes.HTTP_STATUS_OK);
});
